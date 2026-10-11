package com.venuebooking.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.constant.UserRole;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.auth.constant.AuthConstant;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.auth.model.LoginRequest;
import com.venuebooking.module.auth.model.LoginResponse;
import com.venuebooking.module.auth.model.LoginUser;
import com.venuebooking.module.auth.model.RegisterRequest;
import com.venuebooking.module.auth.property.AuthProperties;
import com.venuebooking.module.auth.util.JwtUtil;
import com.venuebooking.module.user.entity.User;
import com.venuebooking.module.user.mapper.UserMapper;
import com.venuebooking.module.user.model.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 认证服务：注册 / 登录 / 登出 / 我的信息。
 *
 * 设计说明：Service 不抽接口、不写 Impl —— 只有一个实现时接口是纯仪式感代码
 * （YAGNI 原则），等真出现第二种实现再抽象不迟。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    /** 注册 */
    public void register(RegisterRequest req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setPhone(req.getPhone());
        user.setRole(UserRole.USER.getCode());
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同名：前置查询挡不住两个请求同时通过校验，
            // 唯一索引是最终防线 —— "先查后插"永远要配数据库兜底
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }
    }

    /** 登录：验密 -> 签发 JWT -> 写 Redis 会话 */
    public LoginResponse login(LoginRequest req) {
        // password 列被 @TableField(select=false) 排除，登录必须显式 select 出来
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getPassword,
                        User::getNickname, User::getRole, User::getStatus)
                .eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            // 统一提示，不区分"用户不存在/密码错误"：防止撞库遍历出有效用户名
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        // 签发 token，并把"当前有效凭证指纹"(jti) 写入 Redis 会话。
        // key 按 userId 存：重复登录覆盖旧 jti -> 旧 token 立即失效（单设备互踢）
        String jti = UUID.randomUUID().toString();
        String token = jwtUtil.create(user.getId(), user.getRole(), user.getUsername(), jti);
        stringRedisTemplate.opsForValue().set(
                AuthConstant.loginKey(user.getId()),
                jti,
                Duration.ofHours(authProperties.getTokenExpireHours()));
        return new LoginResponse(token, user.getId(), user.getNickname(), user.getRole());
    }

    /** 登出：删 Redis 会话 -> token 立刻作废（哪怕 JWT 本身还没过期） */
    public void logout() {
        LoginUser current = CurrentUserContext.get();
        stringRedisTemplate.delete(AuthConstant.loginKey(current.userId()));
    }

    /** 我的信息：以数据库为准（JWT 里的身份快照可能已过期，如改名/换角色） */
    public UserVO me() {
        User user = userMapper.selectById(CurrentUserContext.requireUserId());
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
