package com.venuebooking.common.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置 —— 三个都是保命/保性能的关键件：
 *
 * 1. PaginationInnerInterceptor：分页插件。不配置的话 Page 查询
 *    【不会拼 LIMIT】，会把全表查回内存再"假分页"，数据量一大直接 OOM。
 * 2. OptimisticLockerInnerInterceptor：乐观锁插件。M4 扣库存的
 *    version 方案依赖它（实体带 @Version 时自动拼 version=?）。
 * 3. BlockAttackInnerInterceptor：防全表攻击。没有 WHERE 的
 *    update/delete 直接抛异常 —— 宁可报错也不能误删全表。
 *
 * 注意添加顺序：先"改写 SQL 的"（分页/乐观锁），后"校验 SQL 的"（防全表）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
