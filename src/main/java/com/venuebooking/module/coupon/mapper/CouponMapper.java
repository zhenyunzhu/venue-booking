package com.venuebooking.module.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.coupon.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /** 抢券的 DB 兜底扣减（与场次库存条件更新同一模式），Redis 预扣挡流量、这里保正确 */
    @Update("UPDATE t_coupon SET remain_count = remain_count - 1 WHERE id = #{couponId} AND remain_count > 0")
    int deduct(@Param("couponId") long couponId);
}
