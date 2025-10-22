package com.losgai.sys.service.rental.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.losgai.sys.config.AliPayConfig;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.entity.carRental.RentalOrder;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.RentalOrderMapper;
import com.losgai.sys.service.rental.AlipayService;
import com.losgai.sys.service.rental.CalculationService;
import com.losgai.sys.util.OrderUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static com.losgai.sys.service.rental.impl.OrderServiceImpl.DATE_CACHE_PREFIX;
import static com.losgai.sys.service.rental.impl.OrderServiceImpl.LOCK_KEY;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlipayServiceImpl implements AlipayService {

    private final AlipayClient alipayClient;

    private final AliPayConfig alipayConfig;

    private final RentalOrderMapper rentalOrderMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedissonClient redissonClient;

    private final CalculationService calculationService;

    /**
     * 创建支付订单（页面跳转）
     *
     * @param orderId     订单ID
     * @param totalAmount 订单总金额
     * @param subject     订单标题
     * @return 支付表单HTML
     */
    @Override
    public String createPayment(Long orderId, BigDecimal totalAmount, String subject) {
        try {
            // 创建API对应的request类
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

            // 设置同步回调地址
            request.setReturnUrl(alipayConfig.getReturnUrl());

            // 设置异步回调地址
            request.setNotifyUrl(alipayConfig.getNotifyUrl());

            // 填充业务参数
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(String.valueOf(orderId)); // 商户订单号
            model.setTotalAmount(totalAmount.toString()); // 订单总金额
            model.setSubject(subject); // 订单标题
            model.setProductCode("FAST_INSTANT_TRADE_PAY"); // 产品码
            model.setTimeoutExpress("30m"); // 订单超时时间

            request.setBizModel(model);

            // 调用SDK生成表单
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

            if (response.isSuccess()) {
                log.info("创建支付订单成功，订单号: {}", orderId);
                return response.getBody();
            } else {
                log.error("创建支付订单失败，订单号: {}, 错误信息: {}", orderId, response.getMsg());
                return null;
            }
        } catch (AlipayApiException e) {
            log.error("调用支付宝API异常，订单号: {}", orderId, e);
            return null;
        }
    }

    /**
     * 查询支付订单状态
     *
     * @param orderId 订单ID
     * @return 支付状态 (WAIT_BUYER_PAY-等待付款, TRADE_SUCCESS-支付成功, TRADE_FINISHED-交易完成, TRADE_CLOSED-交易关闭)
     */
    @Override
    public String queryPaymentStatus(Long orderId) {
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(String.valueOf(orderId));

            request.setBizModel(model);

            AlipayTradeQueryResponse response = alipayClient.execute(request);

            if (response.isSuccess()) {
                log.info("查询支付订单状态成功，订单号: {}, 状态: {}", orderId, response.getTradeStatus());
                return response.getTradeStatus();
            } else {
                log.error("查询支付订单状态失败，订单号: {}, 错误信息: {}", orderId, response.getMsg());
                return null;
            }
        } catch (AlipayApiException e) {
            log.error("查询支付宝订单状态异常，订单号: {}", orderId, e);
            return null;
        }
    }

    @Override
    public String getReturnUrl(HttpServletRequest request) {
        try {
            // 获取支付宝返回的参数
            Map<String, String> params = convertRequestParamsToMap(request);

            // 验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );

            if (signVerified) {
                String outTradeNo = params.get("out_trade_no"); // 商户订单号
                String tradeStatus = params.get("trade_status"); // 交易状态

                log.info("支付宝同步回调，订单号: {}, 交易状态: {}", outTradeNo, tradeStatus);

                // 重定向到前端订单详情页
                return "redirect:http://l0sgai.github.io/car-rental-front/order-detail/" + outTradeNo;
            } else {
                log.error("支付宝同步回调签名验证失败");
                return "redirect:http://l0sgai.github.io/car-rental-front/payment-failed";
            }
        } catch (AlipayApiException e) {
            log.error("支付宝同步回调处理异常", e);
            return "redirect:http://l0sgai.github.io/car-rental-front/payment-failed";
        }
    }

    @Override
    public String getNotifyUrl(HttpServletRequest request) {
        try {
            // 获取支付宝返回的参数
            Map<String, String> params = convertRequestParamsToMap(request);

            // 验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );

            if (signVerified) {
                String outTradeNo = params.get("out_trade_no"); // 商户订单号
                String tradeNo = params.get("trade_no"); // 支付宝交易号
                String tradeStatus = params.get("trade_status"); // 交易状态
                String totalAmount = params.get("total_amount"); // 订单金额

                log.info("支付宝异步回调，订单号: {}, 支付宝交易号: {}, 交易状态: {}, 金额: {}",
                        outTradeNo, tradeNo, tradeStatus, totalAmount);

                // 判断交易状态
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    // 更新订单状态，清除预定列表缓存
                    Long orderId = Long.parseLong(outTradeNo);
                    RentalOrder rentalOrder = rentalOrderMapper.selectByPrimaryKey(orderId);
                    if (OrderDateRangeCheck(rentalOrder)) {
                        String lockKey = LOCK_KEY + rentalOrder.getCarId();
                        RLock carLock = redissonClient.getLock(lockKey);
                        boolean isLocked = false;
                        try {
                            // 1. 尝试获取锁
                            isLocked = carLock.tryLock(10, 60, TimeUnit.SECONDS);

                            if (isLocked) {
                                // 2. 获取锁成功，执行核心业务逻辑
                                log.info("线程 {} 获取车辆 {} 的锁成功",
                                        Thread.currentThread().threadId(),
                                        rentalOrder.getCarId());
                                // 更新写入前删除被占时间缓存
                                redisTemplate.delete(DATE_CACHE_PREFIX + rentalOrder.getCarId());
                                // 判断车辆是否正在被租
                                boolean isRenting = OrderUtils.isRenting(rentalOrder.getStartRentalTime());
                                // 更新订单状态
                                rentalOrderMapper.updateStatus(orderId, isRenting ? 2 : 1);
                                log.info("订单 {} 支付成功，已更新订单状态", orderId);
                            } else {
                                // 3. 获取锁失败，说明有其他请求正在处理此车辆的订单
                                log.warn("订单 {} 支付成功，但订单下单时与其它用户冲突，已取消订单",
                                        orderId);
                                rentalOrderMapper.updateStatus(orderId, 5);
                                log.warn("线程 {} 获取车辆 {} 的锁失败，系统繁忙",
                                        Thread.currentThread().threadId(),
                                        rentalOrder.getCarId());
                                // TODO: 退款
                            }
                        } catch (InterruptedException e) {
                            log.error("获取分布式锁时被中断", e);
                            Thread.currentThread().interrupt();
                        } finally {
                            // 4. 无论如何，最后都要释放锁
                            if (isLocked && carLock.isHeldByCurrentThread()) {
                                carLock.unlock();
                                log.info("线程 {} 释放车辆 {} 的锁", Thread.currentThread().threadId(), rentalOrder.getCarId());
                            }
                        }
                    } else {
                        log.warn("订单 {} 支付成功，但订单日期有冲突，已取消订单", orderId);
                        rentalOrderMapper.updateStatus(orderId, 5);
                        // TODO: 退款
                    }
                }
                // 返回success，告知支付宝服务器收到通知
                return "success";
            } else {
                log.error("支付宝异步回调签名验证失败");
                return "failure";
            }
        } catch (Exception e) {
            log.error("支付宝异步回调处理异常", e);
            return "failure";
        }
    }

    /**
     * 将request参数转换为Map
     */
    private Map<String, String> convertRequestParamsToMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();

        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }

        return params;
    }

    /**
     * 检查车辆时间段是否可租
     *
     * @return true/false 是否合法
     */
    private boolean OrderDateRangeCheck(RentalOrder rentalOrder) {
        // 检查车辆日期冲突
        TreeMap<Date, Date> carBookingsAsTreeMap = OrderUtils.
                isBookingTimeAvailable(calculationService.getCarBookingsAsTreeMap(rentalOrder.getCarId()));
        // 如果当前订单的起止时间与缓存中的时间段有冲突，则返回错误码
        Date rentalStartTime = rentalOrder.getStartRentalTime();
        Date rentalEndTime = rentalOrder.getEndRentalTime();

        // 找到小于等于当前起始时间的最近一段
        Map.Entry<Date, Date> floorEntry = carBookingsAsTreeMap.floorEntry(rentalStartTime);
        // 找到大于等于当前起始时间的时间段
        Map.Entry<Date, Date> ceilingEntry = carBookingsAsTreeMap.ceilingEntry(rentalStartTime);

        // 检查与 floorEntry 冲突
        if (floorEntry != null) {
            Date existEnd = floorEntry.getValue();
            if (rentalStartTime.before(existEnd)) {  // 当前订单开始时间 < 已有区间结束时间
                return false;
            }
        }

        // 检查与 ceilingEntry 冲突
        if (ceilingEntry != null) {
            Date existStart = ceilingEntry.getKey();
            // 当前订单结束时间 > 下个区间开始时间
            return !rentalEndTime.after(existStart);
        }

        return true;
    }
}
