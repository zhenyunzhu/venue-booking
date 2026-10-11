package com.venuebooking.module.auth.controller;

import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.model.LoginRequest;
import com.venuebooking.module.auth.model.LoginResponse;
import com.venuebooking.module.auth.model.RegisterRequest;
import com.venuebooking.module.auth.service.AuthService;
import com.venuebooking.module.user.model.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册（公开接口，拦截器白名单） */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.ok();
    }

    /** 登录（公开接口）：成功返回 token，之后请求携带 Header: Authorization: Bearer {token} */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 登出（需登录）：删除 Redis 会话，token 立即作废 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    /** 我的信息（需登录） */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me());
    }
}
