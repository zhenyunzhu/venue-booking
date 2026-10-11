package com.venuebooking.module.waitlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.waitlist.entity.Waitlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WaitlistMapper extends BaseMapper<Waitlist> {

    /** 通知队首：条件更新 0→1，幂等（并发的多次库存回补只会有一次通知成功） */
    @Update("UPDATE t_waitlist SET status = 1, notify_time = NOW() WHERE id = #{id} AND status = 0")
    int markNotified(@Param("id") long id);
}
