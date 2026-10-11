package com.venuebooking.module.venue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.venuebooking.common.constant.CacheKeys;
import com.venuebooking.common.constant.SportType;
import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.metrics.BusinessMetrics;
import com.venuebooking.common.result.PageResult;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.auth.model.LoginUser;
import com.venuebooking.module.venue.component.VenueBloomFilter;
import com.venuebooking.module.venue.component.VenueCacheConfig;
import com.venuebooking.module.venue.entity.Venue;
import com.venuebooking.module.venue.mapper.VenueMapper;
import com.venuebooking.module.venue.model.VenueQuery;
import com.venuebooking.module.venue.model.VenueUpdateRequest;
import com.venuebooking.module.venue.model.VenueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 场馆服务：搜索（不缓存）、详情（缓存三件套全实战）、商家更新（缓存失效演示）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VenueService {

    /** 详情重建互斥锁 key 前缀 */
    private static final String DETAIL_LOCK_PREFIX = "cache:lock:venue:";

    private final VenueMapper venueMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final VenueBloomFilter venueBloomFilter;
    private final Cache<String, VenueVO> venueLocalCache;
    private final BusinessMetrics businessMetrics;

    /**
     * 分页搜索。列表页【刻意不缓存】：城市x类型x关键词x页码的组合是笛卡尔积，
     * 缓存 key 会爆炸且绝大多数 key 命中率极低，白白吃内存。
     * 有 DB 索引（city,type,status）撑着，单表分页毫秒级，收益才去缓存成本。
     */
    public PageResult<VenueVO> search(VenueQuery query) {
        if (query.getSportType() != null && SportType.ofOrNull(query.getSportType()) == null) {
            throw new BizException(ResultCode.INVALID_PARAM);
        }
        long pageSize = Math.min(Math.max(query.getPageSize(), 1), 50);
        long pageNum = Math.max(query.getPageNum(), 1);
        LambdaQueryWrapper<Venue> wrapper = new LambdaQueryWrapper<Venue>()
                .eq(Venue::getStatus, 1)
                .eq(query.getCity() != null && !query.getCity().isBlank(), Venue::getCity, query.getCity())
                .eq(query.getSportType() != null, Venue::getSportType, query.getSportType())
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(), Venue::getName, query.getKeyword())
                .orderByDesc(Venue::getRating)
                .orderByDesc(Venue::getId);
        Page<Venue> page = venueMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, this::toVO);
    }

    /**
     * 场馆详情 —— 多级缓存读链路（M9 演进自 M3 的单级三件套）：
     * L0 布隆过滤器（一定不存在直接拒）→ L1 本地 Caffeine（纳秒级）→
     * L2 Redis（穿透/击穿/雪崩三件套）→ DB 回源。
     */
    public VenueVO getDetail(long id) {
        // ===== L0 布隆过滤器：说"不存在"就一定不存在，恶意 id 在最前置被拦截 =====
        if (!venueBloomFilter.mightContain(id)) {
            businessMetrics.incCacheBloomReject();
            log.info("[布隆] 场馆 {} 一定不存在，直接拒绝", id);
            throw new BizException(ResultCode.VENUE_NOT_FOUND);
        }
        String idKey = String.valueOf(id);
        // ===== L1 本地缓存：进程内存访问，无网络往返，热点场景纳秒级返回 =====
        VenueVO local = venueLocalCache.getIfPresent(idKey);
        if (local != null) {
            businessMetrics.incCacheL1Hit();
            return local;
        }
        // ===== L2 Redis（三件套）+ DB 回源 =====
        VenueVO vo = loadFromL2OrDb(id);
        if (vo != null) {
            venueLocalCache.put(idKey, vo);
        }
        return vo;
    }

    /** L2 Redis + DB 回源：即 M3 的三件套逻辑原样下沉（穿透/击穿/雪崩） */
    private VenueVO loadFromL2OrDb(long id) {
        String key = CacheKeys.venueDetailKey(id);

        // ===== L2 读缓存 =====
        VenueVO cached = readCache(key);
        if (cached != null) {
            businessMetrics.incCacheL2Hit();
            return cached;
        }
        businessMetrics.incCacheMiss();

        // ===== 未命中：抢互斥锁，只放一个请求去重建（防击穿） =====
        String lockKey = DETAIL_LOCK_PREFIX + id;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(locked)) {
            sleepQuietly(80);
            VenueVO retry = readCache(key);
            if (retry != null) {
                return retry;
            }
            throw BizException.of(ResultCode.SYSTEM_ERROR, "查询繁忙，请稍后重试");
        }
        try {
            // 双重检查：拿锁排队期间，缓存可能已被前面的请求重建好
            VenueVO doubleCheck = readCache(key);
            if (doubleCheck != null) {
                return doubleCheck;
            }
            Venue venue = venueMapper.selectById(id);
            if (venue == null || venue.getStatus() == 0) {
                // 穿透防护：连"查无此馆"也缓存（短 TTL），布隆误判漏网的恶意 id 兜底
                stringRedisTemplate.opsForValue().set(key, CacheKeys.NULL_VALUE, Duration.ofMinutes(2));
                throw new BizException(ResultCode.VENUE_NOT_FOUND);
            }
            VenueVO vo = toVO(venue);
            // 雪崩防护：基础 TTL + 随机抖动，避免同批 key 在同一秒集体过期
            long ttlMinutes = 30 + ThreadLocalRandom.current().nextLong(0, 6);
            stringRedisTemplate.opsForValue().set(key, serialize(vo), Duration.ofMinutes(ttlMinutes));
            return vo;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 商家更新场馆（部分字段）。
     * 更新 DB 后【删除】详情缓存 —— Cache Aside 标准写法。
     * 为什么不加 @Transactional：只有一条 update 语句，单语句本身原子，事务注解是仪式感。
     */
    public void updateVenue(long id, VenueUpdateRequest request) {
        Venue venue = venueMapper.selectById(id);
        if (venue == null) {
            throw new BizException(ResultCode.VENUE_NOT_FOUND);
        }
        // 数据权限：拦截器管"角色能不能进"（功能权限），这里管"数据是不是你的"（数据权限）
        LoginUser user = CurrentUserContext.get();
        if (user.role() != UserRole.ADMIN && !Objects.equals(venue.getMerchantUserId(), user.userId())) {
            throw BizException.of(ResultCode.FORBIDDEN, "只能操作自己名下的场馆");
        }
        if (request.getName() != null) {
            venue.setName(request.getName());
        }
        if (request.getDescription() != null) {
            venue.setDescription(request.getDescription());
        }
        if (request.getCoverUrl() != null) {
            venue.setCoverUrl(request.getCoverUrl());
        }
        if (request.getAddress() != null) {
            venue.setAddress(request.getAddress());
        }
        venueMapper.updateById(venue);
        // 多级缓存一致性：L2 删缓存（Cache Aside）+ L1 广播驱逐（所有实例收到后失效本地缓存）
        // 广播是 fire-and-forget，L1 的 60 秒短 TTL 是广播丢失时的最大不一致窗口
        stringRedisTemplate.delete(CacheKeys.venueDetailKey(id));
        venueLocalCache.invalidate(String.valueOf(id));
        stringRedisTemplate.convertAndSend(VenueCacheConfig.INVALID_TOPIC, String.valueOf(id));
    }

    /**
     * 读缓存。返回 null 表示未命中；命中空值标记直接抛"不存在"（穿透防护的另一半）。
     */
    private VenueVO readCache(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        if (CacheKeys.NULL_VALUE.equals(json)) {
            throw new BizException(ResultCode.VENUE_NOT_FOUND);
        }
        try {
            return objectMapper.readValue(json, VenueVO.class);
        } catch (JsonProcessingException e) {
            // 缓存数据损坏按"未命中"处理：删掉坏数据走重建，不让单个坏 key 毒死接口
            log.warn("[缓存] 数据损坏，删除重建: {}", key);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private String serialize(VenueVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "序列化失败");
        }
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private VenueVO toVO(Venue venue) {
        SportType sportType = SportType.ofOrNull(venue.getSportType());
        VenueVO vo = new VenueVO();
        vo.setId(venue.getId());
        vo.setName(venue.getName());
        vo.setSportType(venue.getSportType());
        vo.setSportTypeDesc(sportType == null ? "未知" : sportType.getDesc());
        vo.setCity(venue.getCity());
        vo.setDistrict(venue.getDistrict());
        vo.setAddress(venue.getAddress());
        vo.setCoverUrl(venue.getCoverUrl());
        vo.setDescription(venue.getDescription());
        vo.setOpenTime(venue.getOpenTime());
        vo.setCloseTime(venue.getCloseTime());
        vo.setCourtCount(venue.getCourtCount());
        vo.setRating(venue.getRating());
        vo.setRatingCount(venue.getRatingCount());
        return vo;
    }
}
