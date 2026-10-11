package com.venuebooking.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 字段自动填充：insert 自动填 createTime/updateTime，update 自动填 updateTime。
 *
 * 说明：数据库这两列本身有 DEFAULT CURRENT_TIMESTAMP 兜底，为什么还要在
 * 应用层填一遍？—— 因为 MyBatis-Plus 的 updateById 生成的 SET 子句
 * 不包含数据库的 ON UPDATE 语义覆盖不到的 XML 手写 SQL；应用层统一填，
 * 任何写入路径的时间戳行为都一致，排查问题时不会出现"两个时钟"。
 *
 * strictInsertFill：只填充"实体里声明了 @TableField(fill=...) 且值为 null"的字段，
 * 不会覆盖你手动 set 过的值。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
