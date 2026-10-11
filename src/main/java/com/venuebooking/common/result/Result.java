package com.venuebooking.common.result;

import lombok.Getter;

/**
 * 统一响应体：项目里所有 HTTP 接口，无论成功失败都返回它。
 *
 * 为什么必须包一层：
 * 1. 前端只认一个契约：code=0 即成功，否则按 code 走不同提示；
 *    不包的话，有的接口返回对象、有的返回 null、有的直接抛 500 页面，前端没法写。
 * 2. HTTP 状态码表达"传输层"结果（200/404/500），code 表达"业务层"结果，
 *    两者分开，互不污染。
 *
 * 泛型 T：data 想装什么装什么，配合 Jackson 序列化，列表/分页/对象通吃。
 */
@Getter
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}
