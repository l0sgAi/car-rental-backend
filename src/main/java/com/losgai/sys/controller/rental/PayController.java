package com.losgai.sys.controller.rental;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.StrUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.config.AliPayConfig;
import com.losgai.sys.dto.BookingDto;
import com.losgai.sys.dto.PayDto;
import com.losgai.sys.dto.RentalOrderDto;
import com.losgai.sys.entity.sys.User;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.service.rental.OrderService;
import com.losgai.sys.service.sys.UserService;
import com.losgai.sys.vo.OrderVo;
import com.losgai.sys.vo.ShowOrderVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/payment")
@Slf4j
public class PayController {

    private final AliPayConfig aliPayConfig;

}
