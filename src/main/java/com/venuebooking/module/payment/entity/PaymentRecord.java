package com.venuebooking.module.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水实体（t_payment_record）。
 * trade_no 唯一索引是支付回调幂等的【数据库层兜底】：同一笔第三方交易号只可能落一条流水。
 * 注意：流水表没有逻辑删除——资金记录永不物理消失、也永不逻辑删除（审计要求）。
 */
@Data
@TableName("t_payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    /** 1-微信(模拟) 2-支付宝(模拟) */
    private Integer payType;

    /** 0-支付中 1-成功 2-失败 */
    private Integer status;

    /** 第三方交易号（幂等键） */
    private String tradeNo;

    private LocalDateTime callbackTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
