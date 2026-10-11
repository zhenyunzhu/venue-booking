package com.venuebooking.common.exception;

import com.venuebooking.common.result.Result;
import com.venuebooking.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：Controller / Service 里从此不写 try-catch。
 *
 * 原理一句话：@RestControllerAdvice 是一个"所有 Controller 的 AOP 环绕"，
 * 任何 Controller 方法抛出的异常先经过这里的 @ExceptionHandler 匹配，
 * 匹配到谁就由谁"翻译"成统一响应返回给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：预期内分支，warn 即可（不打堆栈，避免日志噪音） */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("[业务异常] code={}, message={}", e.getResultCode().getCode(), e.getMessage());
        return Result.fail(e.getResultCode(), e.getMessage());
    }

    /** JSON 请求体上的 @Valid 校验失败：把所有字段错误拼成一句话返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ResultCode.INVALID_PARAM.getMessage());
        return Result.fail(ResultCode.INVALID_PARAM, msg);
    }

    /** 表单/Query 参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ResultCode.INVALID_PARAM.getMessage());
        return Result.fail(ResultCode.INVALID_PARAM, msg);
    }

    /** 缺少必填参数：客户端错误，返回 1001 而非 2000 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.fail(ResultCode.INVALID_PARAM, "缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型错误（如 date=abc）：客户端错误 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ResultCode.INVALID_PARAM, "参数类型错误: " + e.getName());
    }

    /**
     * 请求体不是合法 JSON / 编码不是 UTF-8：这是客户端错误，不是系统事故。
     * 单独处理避免它落进兜底 Exception 把 ERROR 日志和告警污染掉。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("[参数错误] 请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.INVALID_PARAM, "请求体格式错误，请检查 JSON 语法与字符编码（UTF-8）");
    }

    /** 兜底：任何没预料到的异常都是系统事故，必须打完整堆栈（error 级别） */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("[系统异常] {}", e.getMessage(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
