package com.venuebooking.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花算法单元测试：纯内存、无外部依赖，跑得飞快。
 * 并发唯一性是最重要的性质——订单号重复是资金级事故。
 */
class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("同一线程生成的 id 严格递增且唯一")
    void shouldGenerateMonotonicUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        long prev = generator.nextId();
        for (int i = 0; i < 10_000; i++) {
            long id = generator.nextId();
            assertTrue(id > prev, "id 必须严格递增（B+ 树友好）");
            prev = id;
        }
    }

    @Test
    @DisplayName("8 线程并发生成 16000 个 id 无重复")
    void shouldGenerateUniqueIdsUnderConcurrency() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        int threads = 8;
        int perThread = 2000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();
        assertEquals((long) threads * perThread, ids.size(), "并发下 id 绝不能重复");
    }

    @Test
    @DisplayName("非法机器号拒绝初始化")
    void shouldRejectInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
    }
}
