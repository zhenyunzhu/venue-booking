package com.venuebooking.module.slot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 场次日历 VO。remainStock/totalStock 用于前端展示"仅剩 N 份"。
 * 注意：缓存里的库存数字允许短暂陈旧（秒级），真正的库存正确性
 * 由 M4 下单时的原子扣减保证 —— 展示可以旧，交易必须准。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueSlotVO {

    private Long id;

    private LocalDate slotDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private BigDecimal price;

    private Integer remainStock;

    private Integer totalStock;
}
