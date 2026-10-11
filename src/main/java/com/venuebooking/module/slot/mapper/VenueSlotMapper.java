package com.venuebooking.module.slot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.slot.entity.VenueSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VenueSlotMapper extends BaseMapper<VenueSlot> {

    /**
     * 条件扣减库存 —— 乐观锁的"条件更新"形态，防超订的 DB 兜底防线：
     * WHERE 带 remain_stock > 0，InnoDB 行锁保证并发原子性，
     * 应用层靠【影响行数】判断成败：=0 即"没抢到"。
     * 库存场景只关心"还有没有"，条件更新比 version CAS 更直接。
     */
    @Update("UPDATE t_venue_slot SET remain_stock = remain_stock - 1 "
            + "WHERE id = #{slotId} AND status = 1 AND remain_stock > 0")
    int deductStock(@Param("slotId") long slotId);

    /** 回补库存（超时关单/取消退款时调用），LEAST 防止回补越过总库存 */
    @Update("UPDATE t_venue_slot SET remain_stock = LEAST(remain_stock + 1, total_stock) "
            + "WHERE id = #{slotId}")
    int restoreStock(@Param("slotId") long slotId);
}
