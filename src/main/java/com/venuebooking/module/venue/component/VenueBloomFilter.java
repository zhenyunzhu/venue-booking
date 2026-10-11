package com.venuebooking.module.venue.component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.module.venue.entity.Venue;
import com.venuebooking.module.venue.mapper.VenueMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 场馆 id 布隆过滤器：防缓存穿透的最前置防线。
 *
 * 原理：一个大位数组 + 多个哈希函数。add 时把元素哈希后置位若干 bit；
 * contains 时只要有一个 bit 为 0 就【一定不存在】（零漏判），
 * 全部为 1 则"可能存在"（有误判率，配置 1%）——所以它只能用于挡"不存在"的请求，
 * 命中后仍要正常走缓存/DB 链路。
 *
 * 与 M3 空值缓存的分工：布隆挡"从未存在的恶意 id"（内存开销恒定，不随攻击增长）；
 * 空值缓存兜"曾经存在后来下架/误判漏网"的请求。两层合用。
 *
 * 已知短板：不能删除元素——场馆物理删除后布隆仍说"可能存在"，
 * 由后续的缓存/DB 状态检查兜底；本系统用逻辑删除，id 永远有效，正好规避。
 */
@Slf4j
@Component
public class VenueBloomFilter implements CommandLineRunner {

    private final RedissonClient redissonClient;
    private final VenueMapper venueMapper;

    private RBloomFilter<String> bloomFilter;

    public VenueBloomFilter(RedissonClient redissonClient, VenueMapper venueMapper) {
        this.redissonClient = redissonClient;
        this.venueMapper = venueMapper;
    }

    @PostConstruct
    public void init() {
        bloomFilter = redissonClient.getBloomFilter("bloom:venue:id");
        // 预期 10 万元素、误判率 1%：Redisson 按此计算位数组大小（约 0.12MB）
        boolean created = bloomFilter.tryInit(100000L, 0.01);
        if (!created) {
            // 多实例/重启时过滤器已存在：复用现有结构（位图共享在 Redis 里）
            log.info("[布隆] 过滤器已初始化过，复用现有结构");
        }
    }

    /** 启动预热：把全部场馆 id 装入布隆过滤器 */
    @Override
    public void run(String... args) {
        List<Venue> venues = venueMapper.selectList(new LambdaQueryWrapper<Venue>().select(Venue::getId));
        venues.forEach(v -> bloomFilter.add(String.valueOf(v.getId())));
        log.info("[布隆] 预热完成，装入场馆 id {} 个", venues.size());
    }

    /** 场馆 id 是否"可能存在" */
    public boolean mightContain(long id) {
        return bloomFilter.contains(String.valueOf(id));
    }

    /** 新上架场馆时登记（扩展点：创建场馆接口接入时调用） */
    public void add(long id) {
        bloomFilter.add(String.valueOf(id));
    }
}
