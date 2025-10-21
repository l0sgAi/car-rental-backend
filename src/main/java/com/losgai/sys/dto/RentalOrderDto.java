package com.losgai.sys.dto;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
    @Size(max= 255,message="编码长度不能超过255")
    @Length(max= 255,message="编码长度不能超过255")
    private String address;
}