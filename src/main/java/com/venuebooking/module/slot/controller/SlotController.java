package com.venuebooking.module.slot.controller;

import com.venuebooking.common.result.Result;
import com.venuebooking.module.slot.model.VenueSlotVO;
import com.venuebooking.module.slot.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 场次日历查询。挂在 /api/venues 路径下符合资源语义：
 * 场次的资源归属是"某场馆的场次"。
 */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    /** 某场馆某天的场次列表：GET /api/venues/{venueId}/slots?date=2026-09-20 */
    @GetMapping("/{venueId}/slots")
    public Result<List<VenueSlotVO>> daySlots(
            @PathVariable long venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(slotService.daySlots(venueId, date));
    }
}
