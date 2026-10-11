package com.venuebooking.module.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CouponDiscountCalculatorTest {

    @Test
    @DisplayName("满减券：直接减减免金额")
    void shouldCalculateFullReduction() {
        assertEquals(new BigDecimal("10.00"),
                CouponDiscountCalculator.calculate(1, new BigDecimal("10.00"), new BigDecimal("60.00")));
    }

    @Test
    @DisplayName("折扣券：金额乘折扣率并保留两位")
    void shouldCalculateRateDiscount() {
        assertEquals(new BigDecimal("12.00"),
                CouponDiscountCalculator.calculate(2, new BigDecimal("0.80"), new BigDecimal("60.00")));
    }

    @Test
    @DisplayName("折扣率 1.0 等于不优惠")
    void shouldHandleZeroDiscountRate() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                CouponDiscountCalculator.calculate(2, new BigDecimal("1.00"), new BigDecimal("60.00"))));
    }

    @Test
    @DisplayName("优惠金额封顶不超过订单金额（防倒贴钱）")
    void shouldCapDiscountAtOrderAmount() {
        assertEquals(new BigDecimal("60.00"),
                CouponDiscountCalculator.calculate(1, new BigDecimal("100.00"), new BigDecimal("60.00")));
    }
}
