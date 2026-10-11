package com.venuebooking.module.auth.util;

import com.venuebooking.module.auth.property.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（jjwt 0.13 API）。
 *
 * JWT 三段式结构：header.payload.signature。
 * payload 只做 Base64URL 编码【不是加密】，任何人拿到都能解开看 ——
 * 所以 payload 绝不能放手机号/密码等敏感信息，只放可公开的身份标识。
 * 防篡改靠第三段签名：改动 payload 任何一个字节，验签都会失败。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final AuthProperties authProperties;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(
                authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。
     *
     * @param jti JWT ID，由调用方生成：它同时是 Redis 会话里记录的
     *            "当前有效凭证指纹"，是主动失效/互踢机制的锚点
     */
    public String create(long userId, int role, String username, String jti) {
        Date now = new Date();
        Date expire = Date.from(now.toInstant()
                .plus(authProperties.getTokenExpireHours(), ChronoUnit.HOURS));
        return Jwts.builder()
                .id(jti)
                .claim("uid", userId)
                .claim("role", role)
                .claim("uname", username)
                .issuedAt(now)
                .expiration(expire)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 + 验签 + 验过期，任何一步失败抛 JwtException，
     * 由拦截器统一翻译成 401 业务异常。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
