package com.venuebooking.module.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。校验规则全写在注解上（声明式校验），
 * 校验失败由全局异常处理器统一翻译成 code=1001，业务代码零 if 判断。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名须为 4~20 位字母/数字/下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度须为 6~32 位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称最长 32 位")
    private String nickname;

    /** 选填：不填提交 null；唯一索引对 NULL 不冲突，允许多个未绑定手机号的用户 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
