package com.venuebooking.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.venuebooking.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper：继承 BaseMapper 后，单表 CRUD 全部免费获得
 * （selectById / insert / updateById / selectPage ...），一行 SQL 都不用写。
 * 复杂联表查询在 M3+ 再写 XML —— 简单的不写 SQL，复杂的写好 SQL，才是正解。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
