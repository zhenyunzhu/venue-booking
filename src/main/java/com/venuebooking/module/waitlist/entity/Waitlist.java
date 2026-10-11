package com.venuebooking.module.waitlist.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候补队列实体（t_waitlist）：满员场次排队，库存回补时自动通知队首。
 * 唯一键 (slot_id, user_id) 防止重复排队。
 */
@Data
@TableName("t_waitlist")
public class Waitlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long slotId;

    private Long userId;

    /** 0-排队中 1-已通知 2-已成交 3-已失效 */
    private Integer status;

    private LocalDateTime notifyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
