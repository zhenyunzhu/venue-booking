package com.venuebooking.module.coupon.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优惠券优惠金额计算器：纯函数、可单元测试穷举边界。
 */
public final class CouponDiscountCalculator {

    private CouponDiscountCalculator() {
    }

    /**
     * @param type          1-满减（discountValue=减免金额） 2-折扣（discountValue=折扣率）
     * @param discountValue 满减额或折扣率
     * @param orderAmount   订单金额
     * @return 优惠金额（永不超过订单金额，避免"倒贴钱"）
     */
    public static BigDecimal calculate(Integer type, BigDecimal discountValue, BigDecimal orderAmount) {
        BigDecimal discount = (type != null && type == 2)
                ? orderAmount.multiply(BigDecimal.ONE.subtract(discountValue)).setScale(2, RoundingMode.HALF_UP)
                : discountValue;
        return discount.min(orderAmount);
    }
}
