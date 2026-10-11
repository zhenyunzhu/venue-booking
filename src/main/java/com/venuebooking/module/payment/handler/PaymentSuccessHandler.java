package com.venuebooking.module.payment.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuebooking.common.outbox.OutboxHandler;
import com.venuebooking.module.notify.service.NotifyService;
import com.venuebooking.module.outbox.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PAY_SUCCESS 消息消费端：支付成功后的下游动作（发核销通知）。
 * 由 Outbox 中继任务调用——支付事务只负责把消息可靠落库，
 * 下游什么时候执行、失败怎么重试，全部与主事务解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessHandler implements OutboxHandler {

    public static final String TYPE = "PAY_SUCCESS";

    private final NotifyService notifyService;
    private final ObjectMapper objectMapper;

    @Override
    public String bizType() {
        return TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            notifyService.sendDirect(
                    payload.get("userId").asLong(),
                    "预订成功",
                    "订单 " + payload.get("orderNo").asText()
                            + " 支付成功，核销码 " + payload.get("checkinCode").asText()
                            + "，到店出示即可入场",
                    1);
        } catch (Exception e) {
            // 抛出去让中继任务按指数退避重试
            throw new IllegalStateException("PAY_SUCCESS 消息处理失败", e);
        }
    }
}
