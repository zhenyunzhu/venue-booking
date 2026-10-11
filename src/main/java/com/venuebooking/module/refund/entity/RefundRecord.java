package com.venuebooking.module.refund.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体（t_refund_record）：资金操作必须单独立单留痕，
 * 订单表只记状态，钱怎么退的、退了多少、谁审的，全在退款单里。
 */
@Data
@TableName("t_refund_record")
public class RefundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private String orderNo;

    private Long userId;

    private BigDecimal refundAmount;

    private String reason;

    /** 0-处理中 1-退款成功 2-已驳回/失败 */
    private Integer status;

    /** 0-系统自动 */
    private Long auditUserId;

    private LocalDateTime finishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
