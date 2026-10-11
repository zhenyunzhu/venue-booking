package com.venuebooking.module.waitlist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.notify.service.NotifyService;
import com.venuebooking.module.slot.entity.VenueSlot;
import com.venuebooking.module.slot.mapper.VenueSlotMapper;
import com.venuebooking.module.waitlist.entity.Waitlist;
import com.venuebooking.module.waitlist.mapper.WaitlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 候补服务：满员排队 + 库存回补后自动通知队首。
 * 与关单/退款路径解耦：任何"库存回补"动作都会调 notifyEarliest。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistMapper waitlistMapper;
    private final VenueSlotMapper slotMapper;
    private final NotifyService notifyService;

    /** 排候补：只有满员场次可排，唯一索引防重复排队 */
    public void join(long slotId, long userId) {
        VenueSlot slot = slotMapper.selectById(slotId);
        if (slot == null || slot.getStatus() == 0) {
            throw new BizException(ResultCode.SLOT_NOT_FOUND);
        }
        if (slot.getRemainStock() != null && slot.getRemainStock() > 0) {
            throw BizException.of(ResultCode.INVALID_PARAM, "该场次尚有余量，可直接预订");
        }
        Waitlist waitlist = new Waitlist();
        waitlist.setSlotId(slotId);
        waitlist.setUserId(userId);
        waitlist.setStatus(0);
        try {
            waitlistMapper.insert(waitlist);
        } catch (DuplicateKeyException e) {
            throw BizException.of(ResultCode.INVALID_PARAM, "您已在候补队列中");
        }
    }

    /** 我在指定场次的候补记录 */
    public List<Waitlist> my(long userId) {
        return waitlistMapper.selectList(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getUserId, userId)
                .orderByDesc(Waitlist::getId)
                .last("LIMIT 50"));
    }

    /**
     * 通知队首（库存回补时调用）：条件更新 0→1 保证并发回补下只通知一次。
     * 被 BookingOrderService 的关单/回补路径调用——"有库存放出"这个事件唯一的事实来源。
     */
    public void notifyEarliest(long slotId) {
        Waitlist first = waitlistMapper.selectOne(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getSlotId, slotId)
                .eq(Waitlist::getStatus, 0)
                .orderByAsc(Waitlist::getId)
                .last("LIMIT 1"));
        if (first == null) {
            return;
        }
        int rows = waitlistMapper.markNotified(first.getId());
        if (rows == 0) {
            return;
        }
        notifyService.sendAsync(first.getUserId(), "候补成功提醒",
                "您候补的场次有名额放出（10 分钟内有效），请尽快下单", 1);
        log.info("[候补] slot={} 已通知用户 {}", slotId, first.getUserId());
    }
}
