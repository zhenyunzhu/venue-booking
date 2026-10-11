package com.venuebooking.module.booking.component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 订单延迟关单队列（基于 Redisson DelayedQueue）。
 *
 * 为什么不用定时轮询扫描订单表：
 * - 轮询有扫描间隔误差，且表大时每次全量条件扫描都是慢查询；
 * - 延迟队列"到点精准投递"，无空轮询。
 *
 * 为什么仍保留兜底扫描（closeExpiredOrders）：队列消息可能因重启/异常丢失，
 * 扫描保证最终一致 —— 队列管时效，扫描管兜底，双保险缺一不可。
 *
 * 多实例安全：底层是 Redis 阻塞队列，take 语义保证每条消息只被一个实例消费。
 */
@Component
@RequiredArgsConstructor
public class OrderDelayQueue {

    private static final String QUEUE_NAME = "order:close:queue";

    private final RedissonClient redissonClient;

    private RBlockingQueue<String> targetQueue;
    private RDelayedQueue<String> delayedQueue;

    @PostConstruct
    public void init() {
        targetQueue = redissonClient.getBlockingQueue(QUEUE_NAME);
        delayedQueue = redissonClient.getDelayedQueue(targetQueue);
    }

    /** 投递延迟消息：到点后自动进入目标队列，被消费者 take 走 */
    public void offer(String orderNo, Duration delay) {
        delayedQueue.offer(orderNo, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 取消消息（支付成功后调用）。已到期转入执行队列的移除不了，由状态机条件更新兜底 */
    public boolean remove(String orderNo) {
        return delayedQueue.remove(orderNo);
    }

    /** 阻塞取消息 */
    public String take() throws InterruptedException {
        return targetQueue.take();
    }
}
