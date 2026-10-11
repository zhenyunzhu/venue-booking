package com.venuebooking.module.user.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息 VO：只暴露该给前端看的字段。
 * VO 与 Entity 必须分离 —— 直接返回实体，字段一变（或新增敏感列）就是泄露事故。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private Integer role;
    private LocalDateTime createTime;
}
