package com.venuebooking.module.slot.controller;

import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.annotation.RequireRole;
import com.venuebooking.module.slot.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台/商家排期管理。生成动作幂等：重复调用只会补缺不会重复插入。
 */
@RestController
@RequestMapping("/api/admin/venues")
@RequiredArgsConstructor
@RequireRole(UserRole.ADMIN)
public class SlotAdminController {

    private final SlotService slotService;

    /** 为场馆批量生成未来 days 天的场次，返回新创建数量 */
    @PostMapping("/{id}/slots/generate")
    public Result<Integer> generate(@PathVariable long id,
                                    @RequestParam(defaultValue = "7") int days) {
        return Result.ok(slotService.generateSlots(id, Math.min(Math.max(days, 1), 30)));
    }
}
