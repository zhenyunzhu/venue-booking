package com.venuebooking.module.refund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.redis.StockRedisHelper;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.common.util.SnowflakeIdGenerator;
import com.venuebooking.module.booking.entity.BookingOrder;
import com.venuebooking.module.booking.mapper.BookingOrderMapper;
import com.venuebooking.module.booking.service.BookingOrderService;
import com.venuebooking.module.refund.entity.RefundRecord;
import com.venuebooking.module.refund.mapper.RefundRecordMapper;
import com.venuebooking.module.refund.model.RefundResult;
import com.venuebooking.module.slot.entity.VenueSlot;
import com.venuebooking.module.slot.mapper.VenueSlotMapper;
import com.venuebooking.module.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 取消与退款服务。
 *
 * 退款规则（可配置化的雏形，生产可演进为规则引擎/策略表）：
 * - 开场前 >= 2 小时申请：全额退款；
 * - 开场前 < 2 小时申请：扣 30% 违约金，退 70%；
 * - 已开场：不支持退款。
 *
 * 防重复退款双保险：Redisson 锁(orderNo) 让"查状态-算金额-改状态-立单"整体串行；
 * 订单状态机条件更新 1→6 做数据库层兜底——即使锁失效也只会成功一次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final String REFUND_LOCK_PREFIX = "lock:refund:";

    private final BookingOrderMapper orderMapper;
    private final VenueSlotMapper slotMapper;
    private final RefundRecordMapper refundMapper;
    private final BookingOrderService bookingOrderService;
    private final WaitlistService waitlistService;
    private final StockRedisHelper stockRedisHelper;
    private final RedissonClient redissonClient;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public RefundResult cancel(String orderNo, long userId) {
        RLock lock = redissonClient.getLock(REFUND_LOCK_PREFIX + orderNo);
        boolean locked;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.SYSTEM_ERROR, "请求被中断，请重试");
        }
        if (!locked) {
            throw BizException.of(ResultCode.RATE_LIMITED, "操作过于频繁，请稍后重试");
        }
        try {
            BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                    .eq(BookingOrder::getOrderNo, orderNo)
                    .eq(BookingOrder::getUserId, userId)
                    .select(BookingOrder::getId, BookingOrder::getOrderNo, BookingOrder::getSlotId,
                            BookingOrder::getUserId, BookingOrder::getStatus, BookingOrder::getPayAmount));
            if (order == null) {
                // 订单不存在/不是你的订单，统一一个提示，不泄露订单存在性
                throw new BizException(ResultCode.ORDER_NOT_FOUND);
            }
            if (order.getStatus() == null || order.getStatus() == 0) {
                return cancelUnpaid(order, userId);
            }
            if (order.getStatus() == 1) {
                return refundPaid(order, userId);
            }
            throw new BizException(ResultCode.ORDER_STATUS_NOT_ALLOWED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 未支付取消：状态机 0→3 + 回补库存（无资金动作，无退款单） */
    private RefundResult cancelUnpaid(BookingOrder order, long userId) {
        int rows = orderMapper.cancelUnpaid(order.getOrderNo(), userId);
        if (rows == 0) {
            return idempotentOrReject(order.getOrderNo(), null);
        }
        bookingOrderService.restoreStockForSlot(order.getSlotId());
        waitlistService.notifyEarliest(order.getSlotId());
        log.info("[取消] 未支付订单 {} 已取消，库存回补", order.getOrderNo());
        return new RefundResult("cancel", false, null, null);
    }

    /** 已支付退款：规则算金额 -> 事务(状态机 1→6 + 退款单) -> 回补库存 */
    private RefundResult refundPaid(BookingOrder order, long userId) {
        VenueSlot slot = slotMapper.selectById(order.getSlotId());
        if (slot == null) {
            throw new BizException(ResultCode.SLOT_NOT_FOUND);
        }
        LocalDateTime start = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        RefundRuleCalculator.RefundPlan plan =
                RefundRuleCalculator.compute(order.getPayAmount(), start, LocalDateTime.now());
        if (!plan.refundable()) {
            throw BizException.of(ResultCode.ORDER_STATUS_NOT_ALLOWED, plan.rule());
        }
        BigDecimal refundAmount = plan.refundAmount();

        String refundNo = "RF" + idGenerator.nextId();
        transactionTemplate.execute(status -> {
            int rows = orderMapper.refundPaid(order.getOrderNo(), userId);
            if (rows == 0) {
                // 并发下另一请求已完成退款：按幂等处理由外层判断
                throw new BizException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "REFUND_CONFLICT");
            }
            RefundRecord record = new RefundRecord();
            record.setRefundNo(refundNo);
            record.setOrderNo(order.getOrderNo());
            record.setUserId(userId);
            record.setRefundAmount(refundAmount);
            record.setReason("用户申请退款");
            record.setStatus(1); // 模拟原路退回即时成功；生产为 0 处理中，等第三方回调改 1
            record.setAuditUserId(0L);
            record.setFinishTime(LocalDateTime.now());
            refundMapper.insert(record);
            return true;
        });

        bookingOrderService.restoreStockForSlot(order.getSlotId());
        waitlistService.notifyEarliest(order.getSlotId());
        log.info("[退款] 订单 {} 退款 {} 元（规则：{}），库存已回补",
                order.getOrderNo(), refundAmount, plan.rule());
        return new RefundResult("refund", false, refundAmount, refundNo);
    }

    /** 影响行数=0 时的分支：已取消/已退款按幂等成功返回，其他状态拒绝 */
    private RefundResult idempotentOrReject(String orderNo, BigDecimal amount) {
        BookingOrder current = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getOrderNo, orderNo)
                .select(BookingOrder::getId, BookingOrder::getStatus));
        if (current != null && (current.getStatus() == 3 || current.getStatus() == 6)) {
            return new RefundResult("cancel", true, amount, null);
        }
        throw new BizException(ResultCode.ORDER_STATUS_NOT_ALLOWED);
    }
}
