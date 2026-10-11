package com.venuebooking.module.booking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.booking.entity.BookingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingOrderMapper extends BaseMapper<BookingOrder> {

    /**
     * 支付成功：状态机条件更新 0(待支付)→1(已支付) + 核销码一次写入。
     * 影响行数=0 说明订单已不是待支付（重复回调/已关单），由调用方按状态分支。
     */
    @Update("UPDATE t_booking_order SET status = 1, pay_time = NOW(), checkin_code = #{checkinCode} "
            + "WHERE order_no = #{orderNo} AND status = 0")
    int markPaid(@Param("orderNo") String orderNo, @Param("checkinCode") String checkinCode);

    /** 超时关单：条件更新 0→4，天然幂等（重复执行影响行数=0） */
    @Update("UPDATE t_booking_order SET status = 4, cancel_time = NOW(), "
            + "cancel_reason = '超时未支付，系统自动关单' WHERE order_no = #{orderNo} AND status = 0")
    int closeIfUnpaid(@Param("orderNo") String orderNo);

    /** 核销：条件更新 1→2 + 记录核销人与时间（并发扫码由行锁串行化，影响行数判重） */
    @Update("UPDATE t_booking_order SET status = 2, checkin_time = NOW(), checkin_staff_id = #{staffId} "
            + "WHERE checkin_code = #{checkinCode} AND status = 1")
    int verifyCheckin(@Param("checkinCode") String checkinCode, @Param("staffId") long staffId);

    /** 未支付取消：条件更新 0→3，幂等 */
    @Update("UPDATE t_booking_order SET status = 3, cancel_time = NOW(), cancel_reason = '用户取消（未支付）' "
            + "WHERE order_no = #{orderNo} AND user_id = #{userId} AND status = 0")
    int cancelUnpaid(@Param("orderNo") String orderNo, @Param("userId") long userId);

    /** 已支付退款：条件更新 1→6（生产更严谨的形态是 1→5 退款中→6，等待第三方确认） */
    @Update("UPDATE t_booking_order SET status = 6, cancel_time = NOW(), cancel_reason = '用户退款（已支付）' "
            + "WHERE order_no = #{orderNo} AND user_id = #{userId} AND status = 1")
    int refundPaid(@Param("orderNo") String orderNo, @Param("userId") long userId);
}
