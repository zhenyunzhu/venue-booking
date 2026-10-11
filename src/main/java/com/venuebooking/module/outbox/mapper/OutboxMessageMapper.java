package com.venuebooking.module.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.outbox.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    /** 标记投递成功（条件更新防并发重复标记） */
    @Update("UPDATE t_outbox_message SET status = 1, finish_time = NOW() WHERE id = #{id} AND status = 0")
    int markSuccess(@Param("id") long id);

    /** 失败重试：指数退避，第 n 次失败后延迟 2^n 分钟（封顶 32 分钟） */
    @Update("UPDATE t_outbox_message SET retry_count = retry_count + 1, "
            + "next_retry_time = DATE_ADD(NOW(), INTERVAL LEAST(POW(2, retry_count), 32) * 60 SECOND) "
            + "WHERE id = #{id} AND status = 0")
    int incrementRetry(@Param("id") long id);

    /** 重试超限进死信，等待人工介入 */
    @Update("UPDATE t_outbox_message SET status = 2 WHERE id = #{id} AND retry_count >= 5")
    int markDeadIfExceeded(@Param("id") long id);
}
