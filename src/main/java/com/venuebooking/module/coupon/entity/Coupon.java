package com.venuebooking.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体（t_coupon）。remain_count 是抢券的"库存"——限时抢券就是第二个秒杀场景。
 */
@Data
@TableName("t_coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 1-满减券（discountValue=减免金额） 2-折扣券（discountValue=折扣率如0.85） */
    private Integer type;

    /** 使用门槛：满 X 元可用，0-无门槛 */
    private BigDecimal thresholdAmount;

    private BigDecimal discountValue;

    private Integer totalCount;

    private Integer remainCount;

    /** 每人限领张数 */
    private Integer perLimit;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    /** 1-上架 0-下架 */
    private Integer status;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
