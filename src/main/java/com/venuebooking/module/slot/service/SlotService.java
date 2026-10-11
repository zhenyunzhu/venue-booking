package com.venuebooking.module.slot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuebooking.common.constant.CacheKeys;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.slot.entity.SlotTemplate;
import com.venuebooking.module.slot.entity.VenueSlot;
import com.venuebooking.module.slot.mapper.SlotTemplateMapper;
import com.venuebooking.module.slot.mapper.VenueSlotMapper;
import com.venuebooking.module.slot.model.VenueSlotVO;
import com.venuebooking.module.venue.entity.Venue;
import com.venuebooking.module.venue.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 场次服务：场次日历查询（缓存）+ 批量排期（幂等）+ 每日定时排期任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    private final VenueSlotMapper slotMapper;
    private final SlotTemplateMapper templateMapper;
    private final VenueMapper venueMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 某场馆某天的场次列表（场次日历）。
     *
     * 缓存策略（与详情刻意不同，讲清楚为什么）：
     * - 空值缓存：无场次的日子也缓存标记，防反复查"未来冷门日期"穿透；
     * - 随机 TTL：防雪崩；
     * - 不加互斥锁：日历的热点程度远低于详情页，击穿概率低且重建成本低
     *   （一条索引查询），为它加锁复杂度不划算 —— 缓存策略要按热点分级，不做一刀切。
     */
    public List<VenueSlotVO> daySlots(long venueId, LocalDate date) {
        // 输入域收敛：只允许查今天起 30 天内 —— 缓存 key 空间有限，恶意日期参数打不穿
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(30))) {
            throw BizException.of(ResultCode.INVALID_PARAM, "日期仅支持今天起 30 天内");
        }
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null || venue.getStatus() == 0) {
            throw new BizException(ResultCode.VENUE_NOT_FOUND);
        }

        String key = CacheKeys.slotDayKey(venueId, date);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            if (CacheKeys.NULL_VALUE.equals(json)) {
                return Collections.emptyList();
            }
            return deserialize(json);
        }

        List<VenueSlot> slots = slotMapper.selectList(new LambdaQueryWrapper<VenueSlot>()
                .eq(VenueSlot::getVenueId, venueId)
                .eq(VenueSlot::getSlotDate, date)
                .eq(VenueSlot::getStatus, 1)
                .orderByAsc(VenueSlot::getStartTime));
        List<VenueSlotVO> vos = slots.stream().map(this::toVO).toList();

        String value = vos.isEmpty() ? CacheKeys.NULL_VALUE : serialize(vos);
        long ttlMinutes = 10 + ThreadLocalRandom.current().nextLong(0, 4);
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttlMinutes));
        return vos;
    }

    /**
     * 按模板批量生成未来 days 天的场次。幂等：已存在的时段跳过，
     * 并发场景由唯一索引 uk_venue_date_time 兜底（DuplicateKeyException 吞掉）。
     * 定时任务/接口双入口都可以随便重放 —— 这就是幂等的意义。
     */
    @Transactional
    public int generateSlots(long venueId, int days) {
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null || venue.getStatus() == 0) {
            throw new BizException(ResultCode.VENUE_NOT_FOUND);
        }
        List<SlotTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<SlotTemplate>()
                .eq(SlotTemplate::getVenueId, venueId)
                .eq(SlotTemplate::getStatus, 1));
        if (templates.isEmpty()) {
            throw new BizException(ResultCode.SLOT_TEMPLATE_EMPTY);
        }

        int created = 0;
        for (int i = 1; i <= days; i++) {
            LocalDate date = LocalDate.now().plusDays(i);
            Set<LocalTime> exists = slotMapper.selectList(new LambdaQueryWrapper<VenueSlot>()
                            .select(VenueSlot::getStartTime)
                            .eq(VenueSlot::getVenueId, venueId)
                            .eq(VenueSlot::getSlotDate, date))
                    .stream().map(VenueSlot::getStartTime).collect(Collectors.toSet());

            boolean inserted = false;
            for (SlotTemplate template : templates) {
                if (exists.contains(template.getStartTime())) {
                    continue;
                }
                VenueSlot slot = new VenueSlot();
                slot.setVenueId(venueId);
                slot.setSlotDate(date);
                slot.setStartTime(template.getStartTime());
                slot.setEndTime(template.getEndTime());
                slot.setPrice(template.getPrice());
                slot.setTotalStock(venue.getCourtCount());
                slot.setRemainStock(venue.getCourtCount());
                slot.setStatus(1);
                try {
                    slotMapper.insert(slot);
                    created++;
                    inserted = true;
                } catch (DuplicateKeyException e) {
                    // 唯一索引兜底：并发重复生成时宁可少插不能重插
                    log.info("[排期] 场次已存在，跳过 venue={} date={} start={}", venueId, date, template.getStartTime());
                }
            }
            if (inserted) {
                // 新排的日期可能已被缓存过（空值标记），删掉保持一致
                stringRedisTemplate.delete(CacheKeys.slotDayKey(venueId, date));
            }
        }
        return created;
    }

    /**
     * 每天 00:05 自动补排未来 7 天场次。
     * 幂等设计：重复执行只是"补缺"，不会重复插入 —— 定时任务必须有这个性质，
     * 因为失败重跑、人工重跑、多实例重复触发都是常态。
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void autoGenerateDaily() {
        List<Venue> venues = venueMapper.selectList(new LambdaQueryWrapper<Venue>()
                .select(Venue::getId)
                .eq(Venue::getStatus, 1));
        for (Venue venue : venues) {
            try {
                int created = generateSlots(venue.getId(), 7);
                log.info("[定时排期] venue={} 新增场次 {} 个", venue.getId(), created);
            } catch (Exception e) {
                // 单个场馆失败不能拖垮整批 —— 任务内 try-catch + 完整堆栈日志
                log.error("[定时排期] venue={} 排期失败", venue.getId(), e);
            }
        }
    }

    private VenueSlotVO toVO(VenueSlot slot) {
        return new VenueSlotVO(slot.getId(), slot.getSlotDate(), slot.getStartTime(),
                slot.getEndTime(), slot.getPrice(), slot.getRemainStock(), slot.getTotalStock());
    }

    private String serialize(List<VenueSlotVO> vos) {
        try {
            return objectMapper.writeValueAsString(vos);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "序列化失败");
        }
    }

    private List<VenueSlotVO> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("[缓存] 日历数据损坏，删除重建");
            return Collections.emptyList();
        }
    }
}
