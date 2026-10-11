package com.venuebooking.module.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.constant.CacheKeys;
import com.venuebooking.module.booking.entity.BookingOrder;
import com.venuebooking.module.booking.mapper.BookingOrderMapper;
import com.venuebooking.module.slot.entity.VenueSlot;
import com.venuebooking.module.slot.mapper.VenueSlotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 两级库存对账任务（Redis 预扣计数器 vs MySQL remain_stock）。
 *
 * 背景：预扣+回补路径再多，也存在极端场景漂移（如事务提交阶段进程崩溃）。
 * 对账是最终防线：发现不一致 → 告警日志 → 以 DB 为准修复 Redis。
 * 生产增强：全量场次对账分批跑、告警接监控、修复前留快照供人工复核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileService {

    private final BookingOrderMapper orderMapper;
    private final VenueSlotMapper slotMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 扫描有活跃订单（待支付/已支付）的场次并比对，返回修复明细 */
    public List<String> reconcile() {
        List<Long> slotIds = orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                        .select(BookingOrder::getSlotId)
                        .in(BookingOrder::getStatus, List.of(0, 1))
                        .groupBy(BookingOrder::getSlotId))
                .stream().map(BookingOrder::getSlotId).distinct().toList();

        List<String> fixes = new ArrayList<>();
        for (Long slotId : slotIds) {
            VenueSlot slot = slotMapper.selectById(slotId);
            if (slot == null || slot.getRemainStock() == null) {
                continue;
            }
            String key = CacheKeys.stockSlotKey(slotId);
            String redisVal = stringRedisTemplate.opsForValue().get(key);
            if (redisVal == null) {
                continue; // 从未参与预扣的场次不比对
            }
            int redisStock = Integer.parseInt(redisVal);
            if (redisStock != slot.getRemainStock()) {
                log.warn("[对账] 场次 {} 两级库存不一致: DB={} Redis={}，以DB为准修复",
                        slotId, slot.getRemainStock(), redisStock);
                stringRedisTemplate.opsForValue().set(key, String.valueOf(slot.getRemainStock()));
                fixes.add("slot " + slotId + ": redis " + redisStock + " -> db " + slot.getRemainStock());
            }
        }
        return fixes;
    }

    /** 每 5 分钟定时对账 */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void scheduledReconcile() {
        List<String> fixes = reconcile();
        if (!fixes.isEmpty()) {
            log.warn("[对账] 定时对账修复 {} 处: {}", fixes.size(), fixes);
        }
    }
}
