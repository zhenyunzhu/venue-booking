package com.venuebooking.module.payment.controller;

import com.venuebooking.common.constant.CacheKeys;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.Result;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.auth.context.CurrentUserContext;
import com.venuebooking.module.payment.property.PaymentProperties;
import com.venuebooking.module.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 支付接口。
 *
 * 两个入口的鉴权方式完全不同：
 * - mock-pay：用户操作，走 JWT；
 * - notify：第三方服务端回调，不走 JWT，走【验签】—— 防"任何人 POST 就能把订单改成已支付"。
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;

    /** 模拟用户支付（真实项目为唤起第三方收银台，支付结果由回调驱动） */
    @PostMapping("/mock-pay/{orderNo}")
    public Result<Void> mockPay(@PathVariable String orderNo) {
        paymentService.mockPay(orderNo, CurrentUserContext.requireUserId());
        return Result.ok();
    }

    /**
     * 模拟第三方支付回调。验签规则（模拟）：X-Pay-Sign = md5(orderNo + "|" + secret)。
     * tradeNo 模拟第三方交易号，同一 tradeNo 重放即验证幂等。
     */
    @PostMapping("/notify/{orderNo}")
    public Result<Void> notify(@PathVariable String orderNo,
                               @RequestHeader(value = "X-Pay-Sign", required = false) String sign,
                               @RequestParam(required = false) String tradeNo) {
        String expected = DigestUtils.md5DigestAsHex(
                (orderNo + "|" + paymentProperties.getNotifySecret()).getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(sign)) {
            throw BizException.of(ResultCode.FORBIDDEN, "支付回调验签失败");
        }
        paymentService.handleNotify(orderNo,
                tradeNo == null || tradeNo.isBlank() ? "NT" + System.nanoTime() : tradeNo);
        return Result.ok();
    }
}
