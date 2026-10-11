package com.venuebooking.module.auth.interceptor;

import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.auth.annotation.RequireRole;
import com.venuebooking.module.auth.constant.AuthConstant;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.auth.model.LoginUser;
import com.venuebooking.module.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 认证 + 鉴权拦截器，每个请求四步：
 *  1. 取 Header: Authorization: Bearer {token}
 *  2. JWT 验签 + 验过期（本地 CPU 完成，无网络开销）
 *  3. Redis 会话校验 —— JWT 自己"撤销不了"，主动失效能力全靠这一步
 *  4. 身份放入 ThreadLocal，再做 @RequireRole 角色检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ---- 1. 取 token ----
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String token = header.substring(BEARER_PREFIX.length());

        // ---- 2. 验签 + 验过期 ----
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            // token 是外部输入，解析失败属预期内的非法输入，warn 即可，不打堆栈
            log.warn("[认证] token 解析失败: {}", e.getMessage());
            throw BizException.of(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        long userId = ((Number) claims.get("uid")).longValue();
        UserRole role = UserRole.of(((Number) claims.get("role")).intValue());
        String username = claims.get("uname", String.class);

        // ---- 3. Redis 会话校验 ----
        String sessionJti = stringRedisTemplate.opsForValue().get(AuthConstant.loginKey(userId));
        if (sessionJti == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        if (!sessionJti.equals(claims.getId())) {
            throw BizException.of(ResultCode.UNAUTHORIZED, "账号已在其他设备登录");
        }

        LoginUser loginUser = new LoginUser(userId, role, username);
        CurrentUserContext.set(loginUser);

        // ---- 4. 角色鉴权 ----
        checkPermission(handler, loginUser);
        return true;
    }

    /** 方法注解优先于类注解；value 中任一角色匹配即放行 */
    private void checkPermission(Object handler, LoginUser user) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return; // 静态资源等非 Controller 请求直接放行
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return; // 没标注解 = 登录即可访问
        }
        boolean allowed = Arrays.stream(requireRole.value()).anyMatch(r -> r == user.role());
        if (!allowed) {
            String need = Arrays.stream(requireRole.value())
                    .map(r -> r.getCode() + "-" + r.getDesc())
                    .collect(Collectors.joining("/"));
            throw BizException.of(ResultCode.FORBIDDEN,
                    "需要权限[" + need + "]，当前角色为 " + user.role().getDesc());
        }
    }

    /** 无论成功还是异常都会执行：ThreadLocal 不清理 = 线程复用后串号 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}
