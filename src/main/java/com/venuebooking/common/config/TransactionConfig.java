package com.venuebooking.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 编程式事务模板。
 *
 * 为什么下单链路用它而不是 @Transactional：
 * 声明式事务的提交发生在方法返回之后（AOP 代理层），而分布式锁在 finally 里
 * 先释放 —— 会出现"锁已易主、事务还没提交"的竞态窗口，下一个人拿到锁后
 * 读不到上一笔未提交的订单，限订校验形同虚设。
 * TransactionTemplate 让事务边界显式化：锁在外、事务在内、提交完成后才放锁。
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
