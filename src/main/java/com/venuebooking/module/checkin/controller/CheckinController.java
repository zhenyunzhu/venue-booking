package com.venuebooking.module.checkin.controller;

import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.annotation.RequireRole;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.booking.model.BookingOrderVO;
import com.venuebooking.module.checkin.model.CheckinRequest;
import com.venuebooking.module.checkin.service.CheckinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 到店核销接口：核销员/商家/管理员可操作（功能权限在注解，数据正确性在状态机）。
 */
@RestController
@RequestMapping("/api/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /** 输入/扫码核销码完成履约；重复扫码幂等返回 */
    @PostMapping("/verify")
    @RequireRole({UserRole.STAFF, UserRole.ADMIN})
    public Result<BookingOrderVO> verify(@Valid @RequestBody CheckinRequest request) {
        return Result.ok(checkinService.verify(request.getCheckinCode(),
                CurrentUserContext.requireUserId()));
    }
}
