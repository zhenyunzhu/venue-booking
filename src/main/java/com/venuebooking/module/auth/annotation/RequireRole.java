package com.venuebooking.module.auth.annotation;

import com.venuebooking.common.constant.UserRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解：声明允许访问的角色集合（任一匹配即放行）。
 * 可标注在 Controller 方法或类上，方法注解优先于类注解。
 * 由 AuthInterceptor 在鉴权阶段读取校验。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    UserRole[] value();
}
