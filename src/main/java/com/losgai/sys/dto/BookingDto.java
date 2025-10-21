package com.losgai.sys.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class BookingDto {
    @NotNull(message = "[车辆ID]不能为空")
    private Long carId;
    @NotNull(message = "[车辆起租日期]不能为空")
    private Date startRentalTime;
    @NotNull(message = "[车辆结束日期]不能为空")
    private Date endRentalTime;
}