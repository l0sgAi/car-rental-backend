package com.losgai.sys.service.rental.impl;

import com.losgai.sys.dto.BookingSlot;
import com.losgai.sys.mapper.RentalOrderMapper;
import com.losgai.sys.service.rental.CalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalculationServiceImpl implements CalculationService {

    private final RentalOrderMapper rentalOrderMapper;

    /**
     * 获取指定车辆未来60天内的预订日历 (已占用的时间段)。
     * 数据以 TreeMap 形式返回，Key为开始日期，Value为结束日期。
     *
     * @param carId 车辆ID
     * @return 一个表示预订日历的 List<BookingSlot>
     *
     * 缓存清除的条件：
     * 1.车辆更新/删除
     * 2.订单支付
     * 3.已支付/租赁中的订单取消或修改时间成功
     */
    @Override
    @Cacheable(value = "carBookingsDateCache", key = "#carId")
    public List<BookingSlot> getCarBookingsAsTreeMap(Long carId) {
        return rentalOrderMapper.findFutureBookingsByCarId(carId);
    }
}
