package com.venuebooking.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户持券实体（t_user_coupon）。
 * 模板字段全部做成【领取时刻的快照】——券模板后续改门槛/面额不影响已发放的券。
 */
@Data
@TableName("t_user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long couponId;

    private String couponTitle;

    private Integer type;

    private BigDecimal thresholdAmount;

    private BigDecimal discountValue;

    /** 0-未使用 1-已使用 2-已过期 */
    private Integer status;

    private LocalDateTime receiveTime;

    private LocalDateTime expireTime;

    private String usedOrderNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
