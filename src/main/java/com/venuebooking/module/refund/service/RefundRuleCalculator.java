package com.venuebooking.module.refund.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 退款规则计算器：纯函数、无副作用、不依赖 Spring。
 *
 * 为什么抽成独立类：退款规则是"资金相关、边界敏感"的核心逻辑，
 * 抽成纯函数后可以用单元测试穷举边界（2 小时整、2 小时差 1 分钟、已开场），
 * 而不用启动整个应用 —— 可测试性本身就是设计质量的体现。
 */
public final class RefundRuleCalculator {

    /** 开场前多少分钟内申请要扣违约金 */
    public static final long FULL_REFUND_BEFORE_MINUTES = 120;

    /** 违约金比例 30% */
    public static final BigDecimal PENALTY_RATE = new BigDecimal("0.30");

    /**
     * @param refundable 是否可退
     * @param refundAmount 应退金额
     * @param rule 命中的规则描述（落日志/返回给前端）
     */
    public record RefundPlan(boolean refundable, BigDecimal refundAmount, String rule) {
    }

    private RefundRuleCalculator() {
    }

    public static RefundPlan compute(BigDecimal payAmount, LocalDateTime slotStart, LocalDateTime now) {
        if (slotStart.isBefore(now)) {
            return new RefundPlan(false, BigDecimal.ZERO, "场次已开场，不支持退款");
        }
        long minutesUntilStart = Duration.between(now, slotStart).toMinutes();
        if (minutesUntilStart >= FULL_REFUND_BEFORE_MINUTES) {
            return new RefundPlan(true, payAmount, "开场前2小时以上，全额退款");
        }
        BigDecimal refund = payAmount
                .multiply(BigDecimal.ONE.subtract(PENALTY_RATE))
                .setScale(2, RoundingMode.HALF_UP);
        return new RefundPlan(true, refund, "开场前不足2小时，扣除30%违约金");
    }
}
