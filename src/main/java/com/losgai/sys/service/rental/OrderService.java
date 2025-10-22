package com.losgai.sys.service.rental;

import com.losgai.sys.dto.BookingDto;
import com.losgai.sys.dto.RentalOrderDto;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.vo.OrderVo;
import com.losgai.sys.vo.ShowOrderVo;
import jakarta.servlet.ServletOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

public interface OrderService {
    OrderVo getStartOrderVo(Long carId);

    ResultCodeEnum create(BookingDto bookingDto);

    void exportOrdersToExcel(OutputStream outputStream) throws IOException;

    List<ShowOrderVo> query(String keyWord, Date startDate, Date endDate, Integer status);

    ResultCodeEnum pay(Long orderId);

    ResultCodeEnum update(RentalOrderDto rentalOrder);

    List<ShowOrderVo> userQuery(String keyWord, Date start, Date end, Integer status);

}
