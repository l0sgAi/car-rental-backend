package com.losgai.sys.controller.rental;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.config.AliPayConfig;
import com.losgai.sys.service.rental.AlipayService;
import com.losgai.sys.service.rental.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.losgai.sys.service.rental.impl.OrderServiceImpl.DATE_CACHE_PREFIX;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/payment")
@Slf4j
public class PayController {

    private final AlipayService alipayService;


    /**
     * 支付宝同步回调（页面跳转）
     */
    @GetMapping("/return")
    public Result<String> returnUrl(HttpServletRequest request) {
        String returnUrl = alipayService.getReturnUrl(request);
        return Result.success(returnUrl);
    }

    /**
     * 支付宝异步回调（服务器通知）
     */
    @PostMapping("/notify")
    public Result<String> notifyUrl(HttpServletRequest request) {
        String notifyUrl = alipayService.getNotifyUrl(request);
        return Result.success(notifyUrl);
    }

}
