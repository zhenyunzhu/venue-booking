package com.venuebooking.common.constant;

import java.time.LocalDate;

/**
 * 缓存 Key 统一收口：key 规则只写一处。
 *
 * NULL_VALUE 是"空值缓存"的标记值：数据库里查不到/没数据也往 Redis 放一个
 * 短 TTL 的标记，恶意重复请求不存在的 id 就永远打不到 DB（防穿透）。
 * 约定：详情缓存里它代表"数据不存在"（抛 1030）；日历缓存里它代表"当天无场次"（空列表）。
 */
public final class CacheKeys {

    public static final String NULL_VALUE = "";

    private CacheKeys() {
    }

    /** 场馆详情：cache:venue:detail:{id} */
    public static String venueDetailKey(long id) {
        return "cache:venue:detail:" + id;
    }

    /** 场馆某天场次：cache:slot:day:{venueId}:{yyyy-MM-dd} */
    public static String slotDayKey(long venueId, LocalDate date) {
        return "cache:slot:day:" + venueId + ":" + date;
    }

    /** 场次库存计数器：stock:slot:{slotId}（DB remain_stock 的 Redis 镜像，用于原子预扣） */
    public static String stockSlotKey(long slotId) {
        return "stock:slot:" + slotId;
    }

    /** 一次性下单凭证：booking:token:{token} -> userId */
    public static String bookingTokenKey(String token) {
        return "booking:token:" + token;
    }

    /** 下单用户锁：lock:booking:user:{userId} */
    public static String bookingUserLockKey(long userId) {
        return "lock:booking:user:" + userId;
    }

    /** 券库存计数器：coupon:stock:{couponId} */
    public static String couponStockKey(long couponId) {
        return "coupon:stock:" + couponId;
    }

    /** 限领标记（per_limit=1 时使用）：coupon:got:{couponId}:{userId} */
    public static String couponGotFlagKey(long couponId, long userId) {
        return "coupon:got:" + couponId + ":" + userId;
    }
}
