package com.venuebooking.module.venue.controller;

import com.venuebooking.common.result.PageResult;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.venue.model.VenueQuery;
import com.venuebooking.module.venue.model.VenueVO;
import com.venuebooking.module.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    /** 场馆分页搜索（登录即可） */
    @GetMapping
    public Result<PageResult<VenueVO>> search(VenueQuery query) {
        return Result.ok(venueService.search(query));
    }

    /** 场馆详情（缓存三件套所在） */
    @GetMapping("/{id}")
    public Result<VenueVO> detail(@PathVariable long id) {
        return Result.ok(venueService.getDetail(id));
    }
}
