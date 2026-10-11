package com.venuebooking.common.exception;

import com.venuebooking.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：Service 层表达"业务规则不满足"的标准出口。
 *
 * 关键认知：抛 BizException 是【正常业务分支】（比如库存不足），
 * 不是程序事故 —— 所以全局处理器只打 warn、不打印堆栈；
 * 而其他 Exception 才是事故，打 error + 完整堆栈。
 * 把这两类日志混在一起，线上排查时会被噪音淹没。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    /** 快捷工厂：throw BizException.of(ResultCode.SLOT_STOCK_NOT_ENOUGH, "该场次仅剩0份"); */
    public static BizException of(ResultCode resultCode, String message) {
        return new BizException(resultCode, message);
    }
}
