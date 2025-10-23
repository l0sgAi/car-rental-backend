package com.losgai.sys.dto;

import lombok.Data;

@Data
public class RefundDto {
    private Long orderId;
    private String refundReason;
}
