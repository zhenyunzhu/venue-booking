package com.venuebooking.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 角色枚举：code 与 t_user.role 字段一一对应。
 * 全项目角色判断只认这个枚举，不再散落魔法数字 1/2/3/4。
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    USER(1, "普通用户"),
    MERCHANT(2, "场馆商家"),
    STAFF(3, "核销员"),
    ADMIN(4, "平台管理员");

    private final int code;
    private final String desc;

    public static UserRole of(int code) {
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色码: " + code);
    }
}
