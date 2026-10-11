package com.venuebooking.module.system.controller;

import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.annotation.RequireRole;
import com.venuebooking.module.booking.service.ReconcileService;
import com.venuebooking.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台管理员接口：类级 @RequireRole —— 本控制器所有方法都需要管理员角色。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireRole(UserRole.ADMIN)
public class AdminController {

    private final UserMapper userMapper;
    private final ReconcileService reconcileService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("time", LocalDateTime.now().withNano(0).toString());
        return Result.ok(data);
    }

    /** 手动触发两级库存对账（定时任务每 5 分钟也会跑一次） */
    @PostMapping("/reconcile")
    public Result<List<String>> reconcile() {
        return Result.ok(reconcileService.reconcile());
    }
}
