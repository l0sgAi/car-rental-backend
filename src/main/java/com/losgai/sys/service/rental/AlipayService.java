package com.losgai.sys.service.rental;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;

public interface AlipayService {

    String createPayment(Long orderId, BigDecimal totalAmount, String subject);

    String queryPaymentStatus(Long orderId);

    String getReturnUrl(HttpServletRequest request);

    String getNotifyUrl(HttpServletRequest request);


}
