package com.venuebooking.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.module.user.entity.User;
import com.venuebooking.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动初始化测试账号（幂等：存在即跳过，重复启动不会重复插入）。
 *
 * 为什么不用 SQL 脚本插用户：BCrypt 每次加密都生成随机盐，
 * 手写在 SQL 里的散列串既容易抄错，也无法和登录校验逻辑保持一致；
 * 由代码生成才保证"存的"和"验的"是同一套算法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createUserIfAbsent("admin", "admin123", "平台管理员", "13800000001", 4);
        createUserIfAbsent("merchant", "merchant123", "场馆商家", "13800000002", 2);
        createUserIfAbsent("staff", "staff123", "核销员", "13800000003", 3);
        createUserIfAbsent("user", "user123", "测试用户", "13800000004", 1);
    }

    private void createUserIfAbsent(String username, String rawPassword, String nickname, String phone, int role) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count != null && count > 0) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("[初始化] 测试账号已创建: {} / {}", username, rawPassword);
    }
}
