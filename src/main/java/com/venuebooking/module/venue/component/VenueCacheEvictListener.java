package com.venuebooking.module.venue.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.venuebooking.module.venue.model.VenueVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 缓存失效广播订阅者：收到"某场馆被更新"的消息后驱逐本实例的 L1 缓存。
 * Redis Pub/Sub 是 fire-and-forget（不持久化），所以 L1 仍保留 60 秒短 TTL 兜底——
 * 可靠性靠 TTL，及时性靠广播，两者配合把不一致窗口压到最小。
 */
@Slf4j
@Component
public class VenueCacheEvictListener implements MessageListener {

    private final Cache<String, VenueVO> venueLocalCache;

    public VenueCacheEvictListener(Cache<String, VenueVO> venueLocalCache) {
        this.venueLocalCache = venueLocalCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String venueId = new String(message.getBody(), StandardCharsets.UTF_8);
        venueLocalCache.invalidate(venueId);
        log.info("[多级缓存] 收到失效广播，驱逐本地缓存 venueId={}", venueId);
    }
}
