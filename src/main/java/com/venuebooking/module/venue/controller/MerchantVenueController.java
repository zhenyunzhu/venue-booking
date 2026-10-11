package com.venuebooking.module.venue.controller;

import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.annotation.RequireRole;
import com.venuebooking.module.venue.model.VenueUpdateRequest;
import com.venuebooking.module.venue.service.VenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家侧场馆管理。更新成功后会删除详情缓存（Cache Aside 一致性演示）。
 */
@RestController
@RequiredArgsConstructor
public class MerchantVenueController {

    private final VenueService venueService;

    /** 商家更新自己场馆的部分字段；管理员可更新任意场馆 */
    @PutMapping("/api/merchant/venues/{id}")
    @RequireRole({UserRole.MERCHANT, UserRole.ADMIN})
    public Result<Void> update(@PathVariable long id, @Valid @RequestBody VenueUpdateRequest request) {
        venueService.updateVenue(id, request);
        return Result.ok();
    }
}
