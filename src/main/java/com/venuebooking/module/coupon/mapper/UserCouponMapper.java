package com.venuebooking.module.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.coupon.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /** 用券：条件更新 0→1（幂等；并发用同一张券只有一个成功） */
    @Update("UPDATE t_user_coupon SET status = 1, used_order_no = #{orderNo} "
            + "WHERE id = #{id} AND user_id = #{userId} AND status = 0")
    int useCoupon(@Param("id") long id, @Param("userId") long userId, @Param("orderNo") String orderNo);
}
