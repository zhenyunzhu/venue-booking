package com.venuebooking.module.notify.service;

import com.venuebooking.module.notify.entity.NotifyMessage;
import com.venuebooking.module.notify.mapper.NotifyMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 通知服务。
 *
 * 两个入口的语义差异：
 * - sendAsync：尽力而为的异步通知（候补提醒等非关键路径），线程池 CallerRuns 保证尽量不丢；
 * - sendDirect：同步落库，供 Outbox 消费端使用——保证"消息标记完成 = 通知已确实落库"，
 *   可靠性由本地消息表的重试机制兜底，而不是靠线程池。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyService {

    private final NotifyMessageMapper notifyMessageMapper;

    /** 异步通知：内部就是同步落库，只是执行线程换成了 notifyExecutor */
    @Async("notifyExecutor")
    public void sendAsync(long userId, String title, String content, int type) {
        try {
            sendDirect(userId, title, content, type);
        } catch (Exception e) {
            log.error("[通知] 异步发送失败 userId={} title={}", userId, title, e);
        }
    }

    /** 同步落库：Outbox 消费端调用，落库失败抛异常 → 消息进入重试 */
    public void sendDirect(long userId, String title, String content, int type) {
        NotifyMessage message = new NotifyMessage();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        message.setType(type);
        message.setIsRead(0);
        notifyMessageMapper.insert(message);
        log.info("[通知] userId={} title={} 已落库并模拟推送", userId, title);
    }
}
