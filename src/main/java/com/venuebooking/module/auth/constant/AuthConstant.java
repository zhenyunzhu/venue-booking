package com.venuebooking.module.auth.constant;

/**
 * 认证相关的 Redis Key 收口在这里：key 规则只写一处，
 * 避免各处手拼字符串拼出不一致的 key（线上排查事故最恨这个）。
 */
public final class AuthConstant {

    /** 登录会话：auth:login:{userId} -> 当前有效 token 的 jti（凭证指纹） */
    public static final String LOGIN_KEY_PREFIX = "auth:login:";

    private AuthConstant() {
    }

    public static String loginKey(long userId) {
        return LOGIN_KEY_PREFIX + userId;
    }
}
