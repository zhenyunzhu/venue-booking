package com.venuebooking.module.coupon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserCouponVO {

    private Long id;

    private String couponTitle;

    private Integer type;

    private BigDecimal thresholdAmount;

    private BigDecimal discountValue;

    private Integer status;

    private LocalDateTime expireTime;

    private String usedOrderNo;
}
