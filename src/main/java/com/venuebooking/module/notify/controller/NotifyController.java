package com.venuebooking.module.notify.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.result.Result;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.notify.entity.NotifyMessage;
import com.venuebooking.module.notify.mapper.NotifyMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifies")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyMessageMapper notifyMessageMapper;

    /** 我的通知（最近 20 条）—— 验证异步通知落库的观察口 */
    @GetMapping("/my")
    public Result<List<NotifyMessage>> my() {
        return Result.ok(notifyMessageMapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, CurrentUserContext.requireUserId())
                .orderByDesc(NotifyMessage::getId)
                .last("LIMIT 20")));
    }
}
