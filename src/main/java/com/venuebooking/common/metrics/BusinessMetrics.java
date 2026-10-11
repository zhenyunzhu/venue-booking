package com.venuebooking.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 业务指标（Micrometer Counter），经 /actuator/prometheus 以 Prometheus 格式暴露。
 *
 * 为什么业务也要打点：技术指标（QPS/RT/JVM）只能告诉你"系统不舒服"，
 * 业务指标才能告诉你"哪里不舒服"——下单拒绝数突增可能是库存热点，
 * 限流拦截数突增可能在被刷，缓存命中率下降可能是一致性逻辑改坏了。
 * 埋点原则：计数器只加不减、在关键分支（成功/各类拒绝）埋、命名带 _total 后缀。
 */
@Component
public class BusinessMetrics {

    private final Counter bookingSuccess;
    private final Counter bookingStockReject;
    private final Counter rateLimited;
    private final Counter cacheL1Hit;
    private final Counter cacheL2Hit;
    private final Counter cacheMiss;
    private final Counter cacheBloomReject;

    public BusinessMetrics(MeterRegistry registry) {
        this.bookingSuccess = Counter.builder("venue_booking_success_total")
                .description("下单成功总数").register(registry);
        this.bookingStockReject = Counter.builder("venue_booking_stock_reject_total")
                .description("库存不足拒绝总数").register(registry);
        this.rateLimited = Counter.builder("venue_rate_limited_total")
                .description("限流拦截总数").register(registry);
        this.cacheL1Hit = Counter.builder("venue_cache_l1_hit_total")
                .description("本地缓存命中总数").register(registry);
        this.cacheL2Hit = Counter.builder("venue_cache_l2_hit_total")
                .description("Redis缓存命中总数").register(registry);
        this.cacheMiss = Counter.builder("venue_cache_miss_total")
                .description("缓存未命中回源DB总数").register(registry);
        this.cacheBloomReject = Counter.builder("venue_cache_bloom_reject_total")
                .description("布隆过滤器拦截总数").register(registry);
    }

    public void incBookingSuccess() {
        bookingSuccess.increment();
    }

    public void incBookingStockReject() {
        bookingStockReject.increment();
    }

    public void incRateLimited() {
        rateLimited.increment();
    }

    public void incCacheL1Hit() {
        cacheL1Hit.increment();
    }

    public void incCacheL2Hit() {
        cacheL2Hit.increment();
    }

    public void incCacheMiss() {
        cacheMiss.increment();
    }

    public void incCacheBloomReject() {
        cacheBloomReject.increment();
    }
}
