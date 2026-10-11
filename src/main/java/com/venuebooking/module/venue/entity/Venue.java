package com.venuebooking.module.venue.entity;

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
 * 场馆实体（t_venue）。court_count 是"同一时段可订份数"，即场次库存的初始值。
 */
@Data
@TableName("t_venue")
public class Venue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantUserId;

    private String name;

    /** 见 SportType 枚举 */
    private Integer sportType;

    private String city;

    private String district;

    private String address;

    private String coverUrl;

    private String description;

    private LocalTime openTime;

    private LocalTime closeTime;

    private Integer courtCount;

    /** 评分冗余字段：评价写入时异步回写，列表页排序免聚合查询 */
    private BigDecimal rating;

    private Integer ratingCount;

    /** 1-上架 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
