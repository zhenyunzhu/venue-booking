package com.venuebooking.module.venue.model;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家更新场馆请求：全部字段选填（只传哪个更新哪个），
 * MP 的 updateById 默认跳过 null 字段，天然支持部分更新。
 */
@Data
public class VenueUpdateRequest {

    @Size(min = 1, max = 64, message = "场馆名称长度须为 1~64")
    private String name;

    @Size(max = 1024, message = "场馆介绍最长 1024 字")
    private String description;

    @Size(max = 255, message = "封面 URL 过长")
    private String coverUrl;

    @Size(max = 255, message = "地址过长")
    private String address;
}
