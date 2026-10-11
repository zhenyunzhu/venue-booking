package com.venuebooking.common.outbox;

import com.venuebooking.module.outbox.entity.OutboxMessage;

/**
 * Outbox 消息处理器：每种业务类型实现一个，由 Spring 收集注册到中继任务。
 *
 * 契约：
 * - handle 必须幂等：投递语义是 at-least-once（进程在"处理成功但标记完成前"崩溃，
 *   消息会被再次投递），消费端要能承受重复；
 * - handle 内做关键动作的【同步】落库，异步只是之后的优化——保证"消息标记完成 =
 *   下游动作已确实完成"。
 */
public interface OutboxHandler {

    /** 处理的业务类型（对应 t_outbox_message.biz_type） */
    String bizType();

    /** 执行下游动作；抛异常则中继任务按指数退避重试，超过次数进死信 */
    void handle(OutboxMessage message);
}
