package com.venuebooking.module.auth.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证配置（application.yml 的 venue.auth 段）。
 * 配置收口成类型安全的属性类，而不是到处 @Value 散着取 —— 改字段名编译期就报错。
 */
@Data
@Component
@ConfigurationProperties(prefix = "venue.auth")
public class AuthProperties {

    /** HMAC 签名密钥：长度必须 >= 32 字节（256 位），生产环境放环境变量/配置中心 */
    private String jwtSecret;

    /** token 有效期（小时） */
    private Integer tokenExpireHours = 24;
}
