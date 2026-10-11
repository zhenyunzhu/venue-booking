package com.venuebooking.module.refund.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 取消/退款结果 VO。
 */
@Data
@AllArgsConstructor
public class RefundResult {

    /** 动作类型：cancel-未支付取消 refund-已支付退款 */
    private String action;

    /** 幂等命中（重复请求返回既有结果） */
    private boolean idempotent;

    private BigDecimal refundAmount;

    private String refundNo;
}
