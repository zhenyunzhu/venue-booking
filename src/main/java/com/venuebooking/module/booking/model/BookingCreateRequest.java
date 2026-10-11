package com.venuebooking.module.booking.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingCreateRequest {

    @NotNull(message = "场次 ID 不能为空")
    private Long slotId;

    /** 选填：使用用户持券 ID 抵扣（M7 实现） */
    private Long userCouponId;
}
