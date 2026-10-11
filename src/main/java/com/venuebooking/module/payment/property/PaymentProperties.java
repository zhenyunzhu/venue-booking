package com.venuebooking.module.payment.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "venue.payment")
public class PaymentProperties {

    /** 回调验签密钥（模拟环境；真实项目由第三方支付平台分配并定期轮换） */
    private String notifySecret;
}
