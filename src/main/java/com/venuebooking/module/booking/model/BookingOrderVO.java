package com.venuebooking.module.booking.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 订单 VO：不暴露 user_id/coupon 等内部字段；status 含义见 t_booking_order 注释。
 */
@Data
public class BookingOrderVO {

    private String orderNo;

    private Long venueId;

    private String venueName;

    private LocalDate slotDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal unitPrice;

    private BigDecimal payAmount;

    private BigDecimal discountAmount;

    private Integer status;

    /** 核销码（支付成功后生成） */
    private String checkinCode;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;
}
