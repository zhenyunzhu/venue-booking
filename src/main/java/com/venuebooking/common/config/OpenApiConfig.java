package com.venuebooking.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置：访问 /swagger-ui.html 查看全部接口，
 * 右上角 Authorize 填入 `Bearer {token}` 后即可带登录态调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI venueOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("趣订场 venue-booking API")
                .description("场馆在线预订与到店核销平台 —— "
                        + "先调 /api/auth/login 获取 token，再在 Authorize 中输入 `Bearer {token}`。"
                        + "错误码分段：0 成功 / 1xxx 业务侧 / 2xxx 系统侧 / 3xxx 第三方。")
                .version("v1.0"));
    }
}
