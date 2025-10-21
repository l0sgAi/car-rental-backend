package com.losgai.sys.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;
/**
 * 用于用户更新订单，他们只能更新订单的起止时间
 * */
@Data
public class RentalOrderDto {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    private Date startRentalTime;
    private Date endRentalTime;
}