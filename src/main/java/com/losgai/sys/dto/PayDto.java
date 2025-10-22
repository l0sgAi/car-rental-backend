package com.losgai.sys.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付宝支付请求对象 所需要的参数
 */
@Data
public class PayDto {
    private String out_trade_no; // 商户订单号 必填
    private String subject; // 订单名称 必填
    private BigDecimal total_amount; // 付款金额 必填
    private String body; // 商品描述 可空
}
