package com.venuebooking.module.slot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 场次模板实体（t_slot_template）：商家配置"几点到几点、多少钱"，
 * 系统按模板批量生成未来 N 天的场次。
 */
@Data
@TableName("t_slot_template")
public class SlotTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long venueId;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal price;

    /** 1-启用 0-停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
