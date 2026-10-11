package com.venuebooking.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端（单机模式，直连 spring.data.redis 配置的实例）。
 *
 * 为什么不引 redisson-spring-boot-starter：starter 会接管整个 Spring Data
 * 连接工厂（替换 Lettuce）并与版本强耦合；我们只需要分布式锁能力，
 * 原生 API 最稳 —— 缓存照旧走 Lettuce，职责分离。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                         @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(16)
                .setConnectTimeout(3000)
                .setTimeout(3000);
        return Redisson.create(config);
    }
}
