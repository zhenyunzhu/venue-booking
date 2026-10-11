package com.venuebooking.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码分段（与 docs/01 架构文档的约定一致）：
 *
 *   0        成功
 *   1xxx     A 段-用户/业务侧错误（参数不合法、状态不允许、库存不足...）
 *   2xxx     B 段-系统侧错误（数据库异常、未知异常...）
 *   3xxx     C 段-第三方/中间件错误（支付渠道...）
 *
 * 为什么用枚举而不是散落的字符串：
 * 错误码是前后端+测试三方共用的"协议"，枚举能防手滑写错、
 * 也能在 IDE 里一键列出全部错误含义。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "ok"),

    // ---- A 段：用户/业务侧 ----
    INVALID_PARAM(1001, "参数不合法"),
    UNAUTHORIZED(1010, "未登录或登录已过期"),
    FORBIDDEN(1011, "无权访问"),
    USER_NOT_FOUND(1020, "用户不存在"),
    USER_ALREADY_EXISTS(1021, "用户名已存在"),
    PASSWORD_ERROR(1022, "用户名或密码错误"),
    ACCOUNT_DISABLED(1023, "账号已被禁用"),
    VENUE_NOT_FOUND(1030, "场馆不存在或已下架"),
    RATE_LIMITED(1401, "请求过于频繁，请稍后再试"),
    SLOT_NOT_FOUND(1040, "场次不存在或已下架"),
    SLOT_STOCK_NOT_ENOUGH(1041, "手慢了，该时段已约满"),
    SLOT_TEMPLATE_EMPTY(1042, "该场馆未配置场次模板"),
    ORDER_NOT_FOUND(1050, "订单不存在"),
    ORDER_STATUS_NOT_ALLOWED(1051, "订单当前状态不支持该操作"),
    ORDER_EXPIRED(1052, "订单已超时关闭"),
    ORDER_DUPLICATE(1053, "您已预订该场次，请勿重复下单"),
    COUPON_NOT_FOUND(1060, "优惠券不存在"),
    COUPON_NOT_ENOUGH(1061, "优惠券已被抢完"),
    COUPON_NOT_USABLE(1062, "优惠券不满足使用条件"),
    CHECKIN_CODE_INVALID(1070, "核销码无效"),

    // ---- B 段：系统侧 ----
    SYSTEM_ERROR(2000, "系统繁忙，请稍后重试"),
    DB_ERROR(2001, "数据访问异常"),

    // ---- C 段：第三方/中间件 ----
    PAY_ERROR(3002, "支付渠道异常");

    private final int code;
    private final String message;
}
