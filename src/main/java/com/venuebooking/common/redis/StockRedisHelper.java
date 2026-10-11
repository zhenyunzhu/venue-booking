package com.venuebooking.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 库存原子操作助手：预扣（Lua）与回补（INCR）统一收口。
 * 场次库存（BookingOrderService）与券库存（CouponService）共用同一套语义，
 * 保证"判断+扣减"在任何业务里都是同一个原子实现。
 */
@Component
public class StockRedisHelper {

    /**
     * Lua：原子预扣。返回 1=成功 0=不足 -1=未初始化。
     * Lua 在 Redis 单线程里整体执行，"查-判断-扣"不会被并发插队。
     */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>(
            "local stock = tonumber(redis.call('GET', KEYS[1])) "
            + "if stock == nil then return -1 end "
            + "if stock < 1 then return 0 end "
            + "redis.call('DECR', KEYS[1]) "
            + "return 1", Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public StockRedisHelper(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 预扣一份库存。key 未初始化时从 DB 读值并 SETNX 原子初始化（用 setIfAbsent
     * 防止"别人已扣减后又被旧值覆盖"的初始化竞态），然后重试一次。
     */
    public Long deduct(String stockKey, Integer dbRemain) {
        Long result = stringRedisTemplate.execute(DEDUCT_SCRIPT, List.of(stockKey));
        if (result != null && result == -1L) {
            stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(dbRemain));
            result = stringRedisTemplate.execute(DEDUCT_SCRIPT, List.of(stockKey));
        }
        return result;
    }

    /** 回补一份（关单/取消退款路径） */
    public Long increment(String stockKey) {
        return stringRedisTemplate.opsForValue().increment(stockKey);
    }
}
