package com.venuebooking.module.slot.entity;

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
 * 场次实体（t_venue_slot）—— 全项目最核心的"时段库存表"。
 * 一行 = 某场馆某天某时段的可售资源，remain_stock 即剩余可订份数。
 */
@Data
@TableName("t_venue_slot")
public class VenueSlot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long venueId;

    private LocalDate slotDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal price;

    private Integer totalStock;

    private Integer remainStock;

    /**
     * 乐观锁版本号。@Version 由 MP 乐观锁插件自动在 UPDATE 时拼 version=?，
     * M4 的库存兜底扣减依赖它。
     */
    @Version
    private Integer version;

    /** 1-可售 0-停售 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
