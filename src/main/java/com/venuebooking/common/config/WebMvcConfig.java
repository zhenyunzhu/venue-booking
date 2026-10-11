package com.venuebooking.common.config;

import com.venuebooking.module.auth.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册认证拦截器。
 *
 * 白名单采用"默认拒绝"原则：保护 /api/** 全部接口，只显式放行公开接口。
 * 反过来"默认放行 + 逐个保护"的做法，新接口上线忘加保护就是越权事故。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/ping",
                        "/api/demo/echo",
                        // 支付回调是第三方服务端调用，不走 JWT，鉴权方式是验签（PaymentController）
                        "/api/payments/notify/**",
                        // API 文档（Swagger UI / OpenAPI JSON）
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html");
    }
}
