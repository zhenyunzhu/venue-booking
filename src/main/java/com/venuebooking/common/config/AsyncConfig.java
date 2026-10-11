package com.venuebooking.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池（支付成功后的发码/站内信等 IO 副作用）。
 *
 * 为什么不让通知阻塞支付主链路：短信/推送是慢 IO，同步执行会把支付回调 RT 拖到秒级；
 * 甩给线程池后主链路微秒级返回。
 *
 * 参数教学：
 * - 核心 2 / 最大 4 / 队列 100：通知是低频非关键任务，小池子足够；
 * - CallerRunsPolicy：队列满时让调用线程自己执行（反压），通知【绝不丢】；
 *   若用 DiscardPolicy 会出现"付了钱没收到码"的资金类客诉；
 * - 优雅停机：等待任务跑完再关，应用发布不丢通知。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("notifyExecutor")
    public Executor notifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
