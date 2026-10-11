package com.venuebooking.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器 —— 订单号等业务 ID 的来源。
 *
 * 64 位结构：1 符号位 + 41 位毫秒时间戳（可用约 69 年）+ 5 位机器号 + 12 位序列。
 * 单机每毫秒可发 4096 个，多实例只要 worker-id 不同就全局不冲突。
 *
 * 为什么订单号不用数据库自增：
 * 1. 自增单号可被遍历（竞对可推算你的单量）；
 * 2. 分库分表后会冲突；
 * 3. 雪花号按时间有序，B+ 树插入友好，且单号本身带时间信息便于排查。
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始纪元：2025-01-01 00:00:00（东八区），延长时间戳可用年限 */
    private static final long EPOCH = 1735660800000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(@Value("${venue.snowflake.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0~" + MAX_WORKER_ID + " 之间");
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            // 机器时钟被回调：继续发号会产出重复 ID，宁可快速失败
            throw new IllegalStateException("检测到时钟回拨 " + (lastTimestamp - timestamp) + "ms，拒绝发号");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒 4096 个序列用尽，自旋等待下一毫秒
                while ((timestamp = System.currentTimeMillis()) <= lastTimestamp) {
                    // spin
                }
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    public String nextOrderNo() {
        return String.valueOf(nextId());
    }
}
