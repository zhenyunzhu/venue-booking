package com.venuebooking.module.coupon.controller;

import com.venuebooking.common.ratelimit.RateLimit;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.coupon.model.CouponVO;
import com.venuebooking.module.coupon.model.UserCouponVO;
import com.venuebooking.module.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 可领券列表 */
    @GetMapping
    public Result<List<CouponVO>> list() {
        return Result.ok(couponService.listAvailable());
    }

    /** 限时抢券：活动洪峰入口，滑动窗口限流（每用户 10 秒 3 次）挡住脚本狂点 */
    @PostMapping("/{couponId}/grab")
    @RateLimit(limit = 3, windowSeconds = 10)
    public Result<Void> grab(@PathVariable long couponId) {
        couponService.grab(CurrentUserContext.requireUserId(), couponId);
        return Result.ok();
    }

    /** 我的券 */
    @GetMapping("/my")
    public Result<List<UserCouponVO>> my() {
        return Result.ok(couponService.myCoupons(CurrentUserContext.requireUserId()));
    }
}
