package com.venuebooking.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运动类型枚举：code 与 t_venue.sport_type 一一对应。
 */
@Getter
@AllArgsConstructor
public enum SportType {

    BADMINTON(1, "羽毛球"),
    BASKETBALL(2, "篮球"),
    SWIMMING(3, "游泳"),
    GYM(4, "健身"),
    TENNIS(5, "网球");

    private final int code;
    private final String desc;

    public static SportType of(int code) {
        for (SportType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知运动类型: " + code);
    }

    /** 查询场景的宽容版：不合法返回 null 而不是抛异常，由调用方决定怎么处理 */
    public static SportType ofOrNull(int code) {
        for (SportType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
