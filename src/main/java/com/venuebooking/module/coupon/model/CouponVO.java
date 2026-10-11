package com.venuebooking.module.coupon.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CouponVO {

    private Long id;

    private String title;

    private Integer type;

    private String typeDesc;

    private BigDecimal thresholdAmount;

    private BigDecimal discountValue;

    private Integer remainCount;

    private Integer perLimit;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;
}
