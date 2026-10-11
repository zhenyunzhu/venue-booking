package com.venuebooking.module.booking.controller;

import com.venuebooking.common.result.CursorPageResult;
import com.venuebooking.common.result.PageResult;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.booking.model.BookingCreateRequest;
import com.venuebooking.module.booking.model.BookingOrderVO;
import com.venuebooking.module.booking.service.BookingOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预订订单接口（核心交易链路）。
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingOrderService bookingOrderService;

    /** 获取一次性下单凭证（防重复提交的第一道闸门），有效期见 venue.booking.token-ttl-minutes */
    @GetMapping("/token")
    public Result<String> token() {
        return Result.ok(bookingOrderService.createToken(CurrentUserContext.requireUserId()));
    }

    /**
     * 抢订下单。Header 携带 X-Idempotent-Token（/token 接口获取）。
     * 流程：凭证校验 -> 用户锁 -> 限订校验 -> 场次校验 -> Lua 预扣 -> DB 兜底。
     */
    @PostMapping
    public Result<BookingOrderVO> create(@Valid @RequestBody BookingCreateRequest request,
            @RequestHeader(value = "X-Idempotent-Token", required = false) String token) {
        return Result.ok(bookingOrderService.create(CurrentUserContext.requireUserId(), request, token));
    }

    /** 我的订单（分页） */
    @GetMapping("/my")
    public Result<PageResult<BookingOrderVO>> my(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(bookingOrderService.myOrders(
                CurrentUserContext.requireUserId(), pageNum, pageSize));
    }

    /** 我的订单-游标分页（深分页优化）：lastId 传上一页的 nextCursor，不传查第一页 */
    @GetMapping("/my/cursor")
    public Result<CursorPageResult<BookingOrderVO>> myCursor(
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "5") long pageSize) {
        return Result.ok(bookingOrderService.myOrdersByCursor(
                CurrentUserContext.requireUserId(), lastId, pageSize));
    }
}
