package com.losgai.sys.service.rental;

import com.losgai.sys.dto.BookingDto;
import com.losgai.sys.dto.RentalOrderDto;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.vo.OrderVo;
import com.losgai.sys.vo.ShowOrderVo;

import java.util.Date;
import java.util.List;

public interface OrderService {
    OrderVo getStartOrderVo(Long carId);

    ResultCodeEnum create(BookingDto bookingDto);

    List<ShowOrderVo> query(String keyWord, Date startDate, Date endDate, Integer status);

    ResultCodeEnum pay(Long orderId);

    ResultCodeEnum update(RentalOrderDto rentalOrder);

    List<ShowOrderVo> userQuery(String keyWord, Date start, Date end, Integer status);
}
