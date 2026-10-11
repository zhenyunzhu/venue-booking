package com.venuebooking.module.venue.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.venuebooking.module.venue.model.VenueVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;

/**
 * 多级缓存配置（L1 本地 Caffeine + L2 Redis）。
 *
 * 为什么要两级：Redis 虽快但每次访问仍有 ~1ms 网络往返 + 序列化开销；
 * 热点场馆（首页推荐位）QPS 上万时，L1 进程内存纳秒级返回，还顺带保护 Redis。
 *
 * 一致性代价与对策：
 * 本地缓存无法像 Redis 一样被"其他实例"直接删除——所以用 Pub/Sub 广播失效：
 * 任一实例更新场馆 → 删 L2 + 发广播 → 所有实例（包括自己）驱逐 L1。
 * 广播是不可靠的（实例宕机会漏收），所以 L1 只给 60 秒短 TTL 作为最大不一致窗口兜底。
 */
@Configuration
public class VenueCacheConfig {

    /** 缓存失效广播频道 */
    public static final String INVALID_TOPIC = "cache:invalid:venue";

    @Bean
    public Cache<String, VenueVO> venueLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                // 短 TTL：即使失效广播全部丢失，本地脏数据最多活 60 秒
                .expireAfterWrite(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public RedisMessageListenerContainer venueCacheListenerContainer(
            RedisConnectionFactory connectionFactory, VenueCacheEvictListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(INVALID_TOPIC));
        return container;
    }
}
