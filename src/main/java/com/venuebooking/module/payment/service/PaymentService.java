package com.venuebooking.module.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.common.util.SnowflakeIdGenerator;
import com.venuebooking.module.booking.component.OrderDelayQueue;
import com.venuebooking.module.booking.entity.BookingOrder;
import com.venuebooking.module.booking.mapper.BookingOrderMapper;
import com.venuebooking.module.outbox.service.OutboxService;
import com.venuebooking.module.payment.entity.PaymentRecord;
import com.venuebooking.module.payment.mapper.PaymentRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付服务：模拟支付 + 第三方回调处理（幂等三保险）。
 *
 * 回调幂等为什么需要"三保险"而不是一层：
 * 1. trade_no 唯一索引 —— 同一笔第三方交易的重放（最常见的重复），DB 层直接拦下；
 * 2. 订单状态机条件更新 0→1 —— 不同交易号但订单已支付的重复回调，影响行数=0 分支处理；
 * 3. 流水与订单状态在同一事务 —— 不存在"扣了钱没出票"。
 *
 * ⚠️ 真实踩坑复盘（本项目的活教材）：初版把 @Transactional 标在 handleNotify 上，
 * 而模拟支付 mockPay 内部 this.handleNotify() 自调用 —— 绕过 AOP 代理导致事务失效，
 * registerSynchronization 抛异常返回 2000，但流水/订单已经各自自动提交，状态割裂。
 * 修复：改用编程式事务（TransactionTemplate），副作用（取消关单消息/发通知）放在
 * 事务返回（=提交）之后显式执行。这也解释了"为什么事务注解会失效"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingOrderMapper orderMapper;
    private final PaymentRecordMapper paymentMapper;
    private final OrderDelayQueue orderDelayQueue;
    private final OutboxService outboxService;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 用户侧模拟支付：真实项目是唤起支付 SDK 拉起收银台，这里直接模拟"第三方回调到达" */
    public void mockPay(String orderNo, long userId) {
        BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getOrderNo, orderNo)
                .eq(BookingOrder::getUserId, userId)
                .select(BookingOrder::getId, BookingOrder::getStatus));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw BizException.of(ResultCode.ORDER_STATUS_NOT_ALLOWED, "订单当前状态不可支付");
        }
        handleNotify(orderNo, "MOCK" + idGenerator.nextId());
    }

    /** 第三方回调入口：orderNo + 第三方交易号。返回核销码（未产生新支付则返回 null） */
    public void handleNotify(String orderNo, String tradeNo) {
        BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getOrderNo, orderNo)
                .select(BookingOrder::getId, BookingOrder::getUserId, BookingOrder::getStatus,
                        BookingOrder::getPayAmount));
        if (order == null) {
            throw BizException.of(ResultCode.ORDER_NOT_FOUND, "回调的订单不存在");
        }

        String paidCode = transactionTemplate.execute(status -> {
            // ===== 保险 1：trade_no 唯一索引，同一笔交易的重放直接幂等返回 =====
            PaymentRecord record = new PaymentRecord();
            record.setPaymentNo("PAY" + idGenerator.nextId());
            record.setOrderNo(orderNo);
            record.setUserId(order.getUserId());
            record.setAmount(order.getPayAmount());
            record.setPayType(1);
            record.setStatus(1);
            record.setTradeNo(tradeNo);
            record.setCallbackTime(LocalDateTime.now());
            try {
                paymentMapper.insert(record);
            } catch (DuplicateKeyException e) {
                log.info("[支付回调] 重复通知 tradeNo={}，幂等返回", tradeNo);
                return null;
            }

            // ===== 保险 2：状态机条件更新 0→1，核销码同语句写入（撞码换号重试） =====
            int rows = 0;
            String checkinCode = null;
            for (int i = 0; i < 3; i++) {
                checkinCode = String.valueOf(10000000 + RANDOM.nextInt(90000000));
                try {
                    rows = orderMapper.markPaid(orderNo, checkinCode);
                    break;
                } catch (DuplicateKeyException e) {
                    log.info("[支付回调] 核销码 {} 撞唯一索引，重试", checkinCode);
                }
            }
            if (rows == 0) {
                BookingOrder current = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                        .eq(BookingOrder::getOrderNo, orderNo)
                        .select(BookingOrder::getId, BookingOrder::getStatus));
                if (current != null && (current.getStatus() == 1 || current.getStatus() == 2)) {
                    log.info("[支付回调] 订单 {} 已支付，不同交易号的重复回调幂等返回", orderNo);
                    return null;
                }
                // 已超时关单后支付才到达：生产环境应自动原路退款（复用退款能力），此处明确拒绝
                throw BizException.of(ResultCode.ORDER_STATUS_NOT_ALLOWED,
                        "订单已超时关闭，支付失败（生产环境将自动退款）");
            }
            // ===== 本地消息表（Outbox）：与支付同一事务写入，事务提交则消息必在，
            // 下游"发核销通知"由中继任务保证最终投递——进程崩溃也不会丢事件 =====
            outboxService.publish("PAY_SUCCESS", orderNo,
                    paymentPayload(order.getUserId(), orderNo, checkinCode));
            return checkinCode;
        });

        // ===== 事务已提交：取消延迟关单消息（尽力而为——即使失败，关单消息到点后
        // 状态机条件更新会幂等跳过，无副作用）=====
        if (paidCode != null) {
            boolean removed = orderDelayQueue.remove(orderNo);
            log.info("[支付回调] 订单 {} 支付成功，核销码 {}，取消延迟关单消息: {}（通知由 Outbox 可靠投递）",
                    orderNo, paidCode, removed);
        }
    }

    private String paymentPayload(long userId, String orderNo, String checkinCode) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "userId", userId, "orderNo", orderNo, "checkinCode", checkinCode));
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "消息体序列化失败");
        }
    }
}
