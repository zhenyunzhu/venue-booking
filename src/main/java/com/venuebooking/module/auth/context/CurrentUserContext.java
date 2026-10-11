package com.venuebooking.module.auth.context;

import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.auth.model.LoginUser;

/**
 * 当前登录用户上下文（ThreadLocal）。
 *
 * 为什么用它：一次 HTTP 请求由 Tomcat 线程池中的一个线程从头处理到尾，
 * 拦截器在入口把解析好的用户身份放进 ThreadLocal，之后 Controller / Service
 * 任意层都能直接取，不用层层传参。
 *
 * ⚠️ 必须 clear()：线程是复用的，不清除的话，下一个请求可能读到上一个用户
 * 的身份 —— 这是真实存在的"串号越权"事故，同时还是内存泄漏。
 * 我们在拦截器 afterCompletion 里统一清理（无论成功还是异常都执行）。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 业务代码取当前用户 ID；上下文为空说明拦截器没跑（路径配错），立即暴露 */
    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
