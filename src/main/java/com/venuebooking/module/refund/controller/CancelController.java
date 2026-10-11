package com.venuebooking.module.refund.controller;

import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.refund.model.RefundResult;
import com.venuebooking.module.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户取消/退款入口（挂在订单资源语义下）。
 * 未支付 → 直接取消；已支付 → 按退款规则退款。
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class CancelController {

    private final RefundService refundService;

    @PostMapping("/{orderNo}/cancel")
    public Result<RefundResult> cancel(@PathVariable String orderNo) {
        return Result.ok(refundService.cancel(orderNo, CurrentUserContext.requireUserId()));
    }
}
