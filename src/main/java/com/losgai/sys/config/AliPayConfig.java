package com.losgai.sys.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 读取yml中的配置信息，自动填充到对应的属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AliPayConfig {
    // appId
    private String appId;
    // app私钥
    private String appPrivateKey;
    // 支付宝公钥
    private String alipayPublicKey;
    // 支付成功后，异步回调的地址
    private String notifyUrl;
    // 返回结果页面url
    private String returnUrl;
    // 网关协议
    private String protocol;
    // 网关域名
    private String gatewayHost;
    // 加密方式
    private String signType;
    // 字符集
    private String charset;
    // 返回格式
    private String format;
    // 网关地址
    private String gatewayUrl;

    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                appPrivateKey,
                format,
                charset,
                alipayPublicKey,
                signType
        );
    }
}
