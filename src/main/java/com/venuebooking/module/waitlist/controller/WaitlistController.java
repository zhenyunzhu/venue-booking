package com.venuebooking.module.waitlist.controller;

import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.waitlist.entity.Waitlist;
import com.venuebooking.module.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waitlists")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    /** 排候补：满员场次排队，库存回补时自动通知队首 */
    @PostMapping("/{slotId}")
    public Result<Void> join(@PathVariable long slotId) {
        waitlistService.join(slotId, CurrentUserContext.requireUserId());
        return Result.ok();
    }

    /** 我的候补记录 */
    @GetMapping("/my")
    public Result<List<Waitlist>> my() {
        return Result.ok(waitlistService.my(CurrentUserContext.requireUserId()));
    }
}
