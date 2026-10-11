package com.venuebooking.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 只注册一个密码加密器 Bean。
 *
 * 为什么只引入 spring-security-crypto 而不是整套 spring-boot-starter-security：
 * starter 会自动装配一整条安全过滤器链（所有接口默认 401），
 * 而我们 M2 的认证方案是 JWT + 拦截器，只需要 BCrypt 这一个工具。
 * 依赖最小化 = 启动更快、行为完全可控。
 *
 * 为什么用 PasswordEncoder 接口而不是 BCryptPasswordEncoder 具体类：
 * 面向接口注入，将来换加密算法（如升级 SCrypt）只改这一处。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
