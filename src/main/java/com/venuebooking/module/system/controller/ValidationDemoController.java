package com.venuebooking.module.system.controller;

import com.venuebooking.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参数校验演示接口（教学用）：
 * 故意提供两个会校验失败的场景，观察 GlobalExceptionHandler 如何把
 * 校验错误统一翻译成 code=1001 的响应 —— 业务代码里一行 if 都不用写。
 */
@RestController
@RequestMapping("/api/demo")
public class ValidationDemoController {

    @Data
    public static class EchoRequest {

        @NotBlank(message = "用户名不能为空")
        private String username;

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
    }

    @PostMapping("/echo")
    public Result<EchoRequest> echo(@Valid @RequestBody EchoRequest request) {
        return Result.ok(request);
    }
}
