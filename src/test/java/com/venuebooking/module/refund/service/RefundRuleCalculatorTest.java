package com.venuebooking.module.refund.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退款规则边界测试：资金逻辑必须穷举边界。
 * 边界点：开场前正好 2 小时（>= 判定为全额）、差 1 分钟（部分退款）、已开场（拒绝）。
 */
class RefundRuleCalculatorTest {

    private static final BigDecimal AMOUNT = new BigDecimal("60.00");
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);

    @Test
    @DisplayName("开场前正好 2 小时：全额退款（>= 边界）")
    void shouldFullyRefundAtExactTwoHours() {
        RefundRuleCalculator.RefundPlan plan =
                RefundRuleCalculator.compute(AMOUNT, now.plusMinutes(120), now);
        assertTrue(plan.refundable());
        assertEquals(new BigDecimal("60.00"), plan.refundAmount());
    }

    @Test
    @DisplayName("开场前差 1 分钟不足 2 小时：退 70%")
    void shouldPartiallyRefundBelowTwoHours() {
        RefundRuleCalculator.RefundPlan plan =
                RefundRuleCalculator.compute(AMOUNT, now.plusMinutes(119), now);
        assertTrue(plan.refundable());
        assertEquals(new BigDecimal("42.00"), plan.refundAmount());
    }

    @Test
    @DisplayName("已开场：不可退")
    void shouldRefundAfterStart() {
        RefundRuleCalculator.RefundPlan plan =
                RefundRuleCalculator.compute(AMOUNT, now.minusMinutes(1), now);
        assertFalse(plan.refundable());
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.refundAmount()));
    }

    @Test
    @DisplayName("折扣金额精度：HALF_UP 保留两位")
    void shouldRoundHalfUpToTwoDecimals() {
        // 100 元退 70% = 70.00；换一个除不尽的组合验证舍入
        RefundRuleCalculator.RefundPlan plan =
                RefundRuleCalculator.compute(new BigDecimal("99.99"), now.plusMinutes(60), now);
        assertEquals(new BigDecimal("69.99"), plan.refundAmount());
    }
}
