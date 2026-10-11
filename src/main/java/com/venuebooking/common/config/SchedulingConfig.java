package com.venuebooking.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务。M3 的排期任务依赖它；
 * 注意：多实例部署时定时任务会重复执行，需要分布式锁/任务分片（M5 后再演进）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
