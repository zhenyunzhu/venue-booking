package com.venuebooking.module.auth.model;

import com.venuebooking.common.constant.UserRole;

/**
 * 登录用户身份（Java 17 record：不可变数据载体，组件即字段，自动生成访问器）。
 */
public record LoginUser(Long userId, UserRole role, String username) {
}
