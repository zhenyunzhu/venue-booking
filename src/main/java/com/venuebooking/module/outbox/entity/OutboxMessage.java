package com.venuebooking.module.outbox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体（t_outbox_message）。
 * 唯一键 (biz_type, biz_key)：同一业务事件只可能落一条消息（发布幂等）。
 */
@Data
@TableName("t_outbox_message")
public class OutboxMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizType;

    private String bizKey;

    private String payload;

    /** 0-待投递 1-已成功 2-死信 */
    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private LocalDateTime finishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
