package com.venuebooking.module.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功响应：前端保存 token，之后每个请求带请求头
 * Authorization: Bearer {token}
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String nickname;
    private Integer role;
}
