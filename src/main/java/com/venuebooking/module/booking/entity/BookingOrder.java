package com.venuebooking.module.booking.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预订订单实体（t_booking_order）—— 核心交易表。
 * 状态机：0-待支付 1-已支付待核销 2-已核销完成 3-已取消 4-超时关单 5-退款中 6-已退款
 * 冗余的场馆名/场次时间/单价是"下单瞬间快照"，历史订单不随场馆改价改名漂移。
 */
@Data
@TableName("t_booking_order")
public class BookingOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号（雪花算法生成，唯一） */
    private String orderNo;

    private Long userId;

    private Long venueId;

    private String venueName;

    private Long slotId;

    private LocalDate slotDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

    private BigDecimal payAmount;

    /** 0-未用券 */
    private Long couponId;

    private Long userCouponId;

    private Integer status;

    /** 支付截止时间（超时自动关单） */
    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private String checkinCode;

    private LocalDateTime checkinTime;

    private Long checkinStaffId;

    private LocalDateTime cancelTime;

    private String cancelReason;

    private String remark;

    /** 订单并发编辑（M6 退款审核）走乐观锁 version */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
