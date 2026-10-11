package com.venuebooking.module.booking.component;

import com.venuebooking.module.booking.service.BookingOrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 超时关单消费者：单线程死循环阻塞 take，单条失败不影响后续消费。
 *
 * 为什么用独立线程而不是 @Scheduled 轮询：take 阻塞式消费零延迟、零空转。
 * 优雅停机：@PreDestroy 里置停 + 中断，发布应用不丢正在处理的消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseConsumer {

    private final OrderDelayQueue orderDelayQueue;
    private final BookingOrderService bookingOrderService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread worker;

    @PostConstruct
    public void start() {
        worker = new Thread(() -> {
            log.info("[延迟队列] 超时关单消费者已启动");
            while (running.get()) {
                try {
                    String orderNo = orderDelayQueue.take();
                    log.info("[延迟队列] 收到关单消息: {}", orderNo);
                    bookingOrderService.closeOrderIfUnpaid(orderNo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 单条消息处理失败只记日志不中断消费；状态机条件更新保证可重放
                    log.error("[延迟队列] 关单处理失败", e);
                }
            }
            log.info("[延迟队列] 超时关单消费者已退出");
        }, "order-close-consumer");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }
}
