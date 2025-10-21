package com.losgai.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

@Data
public class BookingDto {
    @NotNull(message = "[车辆ID]不能为空")
    private Long carId;
    @NotNull(message = "[车辆起租日期]不能为空")
    private Date startRentalTime;
    @NotNull(message = "[车辆结束日期]不能为空")
    private Date endRentalTime;
    @NotBlank(message = "[地址]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @Length(max= 255,message="编码长度不能超过255")
    private String address;
}