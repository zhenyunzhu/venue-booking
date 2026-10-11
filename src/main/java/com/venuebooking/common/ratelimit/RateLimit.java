package com.venuebooking.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（滑动窗口，Redis ZSET + Lua 实现）。
 * 标注在 Controller 方法上，由 RateLimitAspect 环绕拦截。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求数 */
    int limit() default 10;

    /** 窗口时长（秒） */
    int windowSeconds() default 10;

    /** 业务 key 后缀；留空使用方法签名 */
    String key() default "";

    /** true=按用户维度限流（登录态）；false=全局限流 */
    boolean perUser() default true;
}
