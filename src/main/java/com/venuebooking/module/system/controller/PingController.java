package com.venuebooking.module.system.controller;

import com.venuebooking.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 环境自检接口：一次请求同时验证 MySQL 与 Redis 的连通性。
 * M1 的验收标准就是它返回 db=ok、redis=ok。
 */
@Slf4j
@RestController
@RequestMapping("/api/ping")
@RequiredArgsConstructor
public class PingController {

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    @GetMapping
    public Result<Map<String, String>> ping() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("db", checkDatabase());
        status.put("redis", checkRedis());
        return Result.ok(status);
    }

    private String checkDatabase() {
        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            return "ok (" + version + ")";
        } catch (Exception e) {
            log.error("[自检] MySQL 连接失败", e);
            return "FAIL: " + e.getMessage();
        }
    }

    private String checkRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            return "ok (" + connection.ping() + ")";
        } catch (Exception e) {
            log.error("[自检] Redis 连接失败", e);
            return "FAIL: " + e.getMessage();
        }
    }
}
