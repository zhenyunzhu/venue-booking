package com.venuebooking.module.venue.model;

import lombok.Data;

/**
 * 场馆搜索查询参数（GET 查询串自动绑定到该对象）。
 */
@Data
public class VenueQuery {

    private String city;

    private Integer sportType;

    private String keyword;

    private long pageNum = 1;

    private long pageSize = 10;
}
