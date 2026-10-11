package com.venuebooking.module.venue.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 场馆 VO：列表与详情共用（详情多 description/address，列表返回时值为空即可，
 * 避免维护两套结构；字段多了再拆）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueVO {

    private Long id;

    private String name;

    private Integer sportType;

    private String sportTypeDesc;

    private String city;

    private String district;

    private String address;

    private String coverUrl;

    private String description;

    private LocalTime openTime;

    private LocalTime closeTime;

    private Integer courtCount;

    private BigDecimal rating;

    private Integer ratingCount;
}
