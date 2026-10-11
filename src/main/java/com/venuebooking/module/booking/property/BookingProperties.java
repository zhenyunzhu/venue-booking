package com.venuebooking.module.booking.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 预订/交易相关配置（application.yml 的 venue.booking 段）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "venue.booking")
public class BookingProperties {

    /** 支付截止分钟数：超时自动关单（M5 延迟队列） */
    private Integer payExpireMinutes = 15;

    /** 一次性下单凭证有效期（分钟） */
    private Integer tokenTtlMinutes = 5;
}
