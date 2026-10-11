package com.venuebooking.module.outbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.outbox.OutboxHandler;
import com.venuebooking.module.outbox.entity.OutboxMessage;
import com.venuebooking.module.outbox.mapper.OutboxMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 本地消息表（Outbox）服务：发布 + 中继。
 *
 * 发布（publish）：必须在业务事务内调用——消息与业务数据同生共死，
 * 事务提交则消息必在，这就是 Outbox 的核心：把"发消息"变成"写本地表"。
 *
 * 中继（relay）：定时扫描待投递消息分发给 OutboxHandler：
 * - 成功 → 标记完成（条件更新防并发重复标记）；
 * - 失败 → 指数退避重试（2^n 分钟），超过 5 次进死信等待人工介入；
 * - 每条消息加分布式锁抢占，多实例部署不重复消费；
 * - 投递语义 at-least-once，消费端 handler 必须幂等。
 */
@Slf4j
@Service
public class OutboxService {

    private final OutboxMessageMapper outboxMapper;
    private final Map<String, OutboxHandler> handlerMap;
    private final RedissonClient redissonClient;

    public OutboxService(OutboxMessageMapper outboxMapper,
                         List<OutboxHandler> handlers,
                         RedissonClient redissonClient) {
        this.outboxMapper = outboxMapper;
        this.redissonClient = redissonClient;
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(OutboxHandler::bizType, Function.identity()));
        log.info("[Outbox] 已注册处理器: {}", handlerMap.keySet());
    }

    /** 事务内发布消息：唯一索引 (biz_type, biz_key) 保证同一业务事件只发布一次 */
    public void publish(String bizType, String bizKey, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.setBizType(bizType);
        message.setBizKey(bizKey);
        message.setPayload(payload);
        message.setStatus(0);
        message.setRetryCount(0);
        message.setNextRetryTime(LocalDateTime.now());
        try {
            outboxMapper.insert(message);
        } catch (DuplicateKeyException e) {
            // 重复回调等场景的重复发布：幂等忽略
            log.info("[Outbox] 重复发布已忽略: {}/{}", bizType, bizKey);
        }
    }

    /** 中继任务：每 10 秒扫描一批到期消息 */
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void relay() {
        List<OutboxMessage> pending = outboxMapper.selectList(new LambdaQueryWrapper<OutboxMessage>()
                .eq(OutboxMessage::getStatus, 0)
                .le(OutboxMessage::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OutboxMessage::getId)
                .last("LIMIT 50"));
        for (OutboxMessage message : pending) {
            relayOne(message);
        }
    }

    private void relayOne(OutboxMessage message) {
        OutboxHandler handler = handlerMap.get(message.getBizType());
        if (handler == null) {
            log.error("[Outbox] 未注册处理器: {}", message.getBizType());
            return;
        }
        RLock lock = redissonClient.getLock("lock:outbox:" + message.getId());
        // 非阻塞抢占：多实例部署时同一消息只被一个实例处理
        boolean locked = lock.tryLock();
        if (!locked) {
            return;
        }
        try {
            handler.handle(message);
            outboxMapper.markSuccess(message.getId());
            log.info("[Outbox] 投递成功 type={} key={}", message.getBizType(), message.getBizKey());
        } catch (Exception e) {
            log.error("[Outbox] 投递失败 id={} type={} retry={}",
                    message.getId(), message.getBizType(), message.getRetryCount(), e);
            outboxMapper.incrementRetry(message.getId());
            outboxMapper.markDeadIfExceeded(message.getId());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
