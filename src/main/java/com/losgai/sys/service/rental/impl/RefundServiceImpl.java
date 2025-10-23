package com.losgai.sys.service.rental.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.losgai.sys.config.AliPayConfig;
import com.losgai.sys.dto.RefundDto;
import com.losgai.sys.entity.carRental.RentalOrder;
import com.losgai.sys.mapper.RentalOrderMapper;
import com.losgai.sys.service.rental.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final AliPayConfig alipayConfig;

    private final RentalOrderMapper rentalOrderMapper;

    @Override
    public void refund(RefundDto refundDto) {
        Long orderId = refundDto.getOrderId();
        String refundReason = refundDto.getRefundReason();
        try {
            // 1. 查询订单信息
            RentalOrder order = rentalOrderMapper.selectByPrimaryKey(orderId);
            if (order == null) {
                log.error("退款失败：订单不存在，订单ID: {}", orderId);
                return;
            }

            // 0-待支付、1-已支付、5-待退款
            if (order.getStatus() != 0 && order.getStatus() != 1 && order.getStatus() != 5) {
                log.error("订单状态错误，无法退款，订单ID: {}", orderId);
                return;
            }

            // 必须有 trade_no 才能退款
            if (StrUtil.isBlank(order.getTradeNo())) {
                log.error("退款失败：订单未保存支付宝交易号 trade_no，无法退款，订单ID: {}", orderId);
                return;
            }

            // 3. 构建退款请求
            AlipayClient alipayClient = new DefaultAlipayClient(
                    alipayConfig.getGatewayUrl(),
                    alipayConfig.getAppId(),
                    alipayConfig.getAppPrivateKey(),
                    "json",
                    alipayConfig.getCharset(),
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getSignType()
            );

            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("trade_no", order.getTradeNo()); // 商户订单号
            bizContent.put("refund_amount", order.getPrice()); // 退款金额
            bizContent.put("refund_reason", refundReason); // 退款原因
            bizContent.put("out_request_no", "TK" + orderId + System.currentTimeMillis()); // 退款单号（唯一）

            request.setBizContent(bizContent.toString());

            // 4. 发起退款请求
            AlipayTradeRefundResponse response = alipayClient.execute(request);

            // 5. 处理退款结果
            if (response.isSuccess()) {
                log.info("退款成功，订单ID: {}, 退款金额: {}, 退款原因: {}",
                        orderId, order.getPrice(), refundReason);
                // 更新订单状态为已退款
                rentalOrderMapper.updateStatus(orderId, 6);
            } else {
                log.error("退款失败，订单ID: {}, 错误码: {}, 错误信息: {}",
                        orderId, response.getCode(), response.getMsg());
            }
        } catch (AlipayApiException e) {
            log.error("调用支付宝退款接口异常，订单ID: {}", orderId, e);
        } catch (Exception e) {
            log.error("退款处理异常，订单ID: {}", orderId, e);
        }
    }
}
