package com.venuebooking.module.checkin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.booking.entity.BookingOrder;
import com.venuebooking.module.booking.mapper.BookingOrderMapper;
import com.venuebooking.module.booking.model.BookingOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 到店核销服务：核销员/商家输入（扫）核销码完成履约。
 *
 * 并发防重：verifyCheckin 是"条件更新 1→2"，行锁串行化 + 影响行数判重——
 * 两个核销员同时扫同一单，只有一个 rows=1，另一个按幂等返回已核销。
 * 不需要分布式锁：单行条件更新本身就是原子的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final BookingOrderMapper orderMapper;

    public BookingOrderVO verify(String checkinCode, long staffId) {
        BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getCheckinCode, checkinCode)
                .select(BookingOrder::getId, BookingOrder::getOrderNo, BookingOrder::getStatus,
                        BookingOrder::getVenueName, BookingOrder::getSlotDate, BookingOrder::getStartTime,
                        BookingOrder::getEndTime, BookingOrder::getPayAmount, BookingOrder::getDiscountAmount,
                        BookingOrder::getCheckinCode, BookingOrder::getCreateTime, BookingOrder::getExpireTime));
        if (order == null) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID);
        }
        int rows = orderMapper.verifyCheckin(checkinCode, staffId);
        if (rows == 0) {
            BookingOrder current = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                    .eq(BookingOrder::getCheckinCode, checkinCode)
                    .select(BookingOrder::getId, BookingOrder::getStatus));
            if (current != null && current.getStatus() == 2) {
                log.info("[核销] 核销码 {} 重复扫码，幂等返回", checkinCode);
            } else {
                throw new BizException(ResultCode.ORDER_STATUS_NOT_ALLOWED, "订单不是待核销状态");
            }
        } else {
            log.info("[核销] 核销码 {} 核销成功，操作员 {}", checkinCode, staffId);
        }
        return toVO(order);
    }

    private BookingOrderVO toVO(BookingOrder order) {
        BookingOrderVO vo = new BookingOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setVenueName(order.getVenueName());
        vo.setSlotDate(order.getSlotDate());
        vo.setStartTime(order.getStartTime());
        vo.setEndTime(order.getEndTime());
        vo.setPayAmount(order.getPayAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setStatus(order.getStatus());
        vo.setCheckinCode(order.getCheckinCode());
        vo.setCreateTime(order.getCreateTime());
        vo.setExpireTime(order.getExpireTime());
        return vo;
    }
}
