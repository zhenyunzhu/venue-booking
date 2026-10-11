package com.venuebooking.common.ratelimit;

import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.metrics.BusinessMetrics;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.auth.context.CurrentUserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 限流切面：Redis ZSET 滑动窗口。
 *
 * 为什么选滑动窗口而不是固定窗口：
 * 固定窗口在窗口边界处可能放行 2 倍流量（两个窗口的交界 1 秒内通过 2×limit）；
 * 滑动窗口按"最近 N 秒内的请求数"计数，无边界突变。
 *
 * 为什么用 ZSET + Lua：
 * ZSET member=请求唯一标识、score=毫秒时间戳；Lua 把"清理窗口外成员→计数→写入"
 * 三步做成原子，并发下计数不会漏或多。
 *
 * 限流的位置在 AOP 层：比拦截器更靠近业务，可以按业务语义配维度（用户/全局）
 * 与阈值，不同接口不同策略——这也是它比网关层限流灵活的地方（网关限流做粗粒度兜底）。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /** 返回 1=放行 0=拒绝。ARGV: 1-当前毫秒 2-本次请求唯一成员 3-限流阈值 4-窗口毫秒 5-窗口起点毫秒 */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>(
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[5]) "
            + "local count = redis.call('ZCARD', KEYS[1]) "
            + "if count < tonumber(ARGV[3]) then "
            + "  redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]) "
            + "  redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[4])) "
            + "  return 1 "
            + "end "
            + "return 0", Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final BusinessMetrics businessMetrics;

    public RateLimitAspect(StringRedisTemplate stringRedisTemplate, BusinessMetrics businessMetrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.businessMetrics = businessMetrics;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String suffix = rateLimit.key().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : rateLimit.key();
        String dimension = rateLimit.perUser() && CurrentUserContext.get() != null
                ? String.valueOf(CurrentUserContext.get().userId())
                : "global";
        String key = "ratelimit:" + suffix + ":" + dimension;
        long now = System.currentTimeMillis();

        Long allowed = stringRedisTemplate.execute(SLIDING_WINDOW_SCRIPT,
                List.of(key),
                String.valueOf(now),
                now + "-" + UUID.randomUUID(),
                String.valueOf(rateLimit.limit()),
                String.valueOf(rateLimit.windowSeconds() * 1000L),
                String.valueOf(now - rateLimit.windowSeconds() * 1000L));

        if (allowed == null || allowed != 1L) {
            businessMetrics.incRateLimited();
            log.info("[限流] 拦截 key={}", key);
            throw BizException.of(ResultCode.RATE_LIMITED,
                    "请求过于频繁（" + rateLimit.limit() + "次/" + rateLimit.windowSeconds() + "秒），请稍后再试");
        }
        return joinPoint.proceed();
    }
}
