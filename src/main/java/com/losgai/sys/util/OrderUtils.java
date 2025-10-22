package com.losgai.sys.util;

import cn.hutool.core.collection.CollUtil;
import com.losgai.sys.dto.BookingSlot;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class OrderUtils {

    /**
     * 判断指定时间段内是否已存在预订记录
     *
     * @param list 预订记录列表
     * @return 一个表示预订日历的 TreeMap
     */
    public static TreeMap<Date, Date> isBookingTimeAvailable(List<BookingSlot> list) {
        if(CollUtil.isEmpty(list)){
            return new TreeMap<>();
        }

        // 2. 将 List<BookingSlot> 转换为 TreeMap
        return list.stream()
                .collect(Collectors.toMap(
                        BookingSlot::getStartRentalTime, // Key
                        BookingSlot::getEndRentalTime,   // Value
                        (v1, v2) -> v2, // 如果出现重复的key，保留后者
                        TreeMap::new    // 指定Map的实现为TreeMap
                ));
    }

    /**
     * 判断是否需要订单更新状态为租赁中
     * */
    public static boolean isRenting(Date start) {
        // 今天日期
        LocalDate today = LocalDate.now();
        // 订单开始租借日期
        LocalDate startDate = start
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        // 只要离开始日期还有1天就算租赁中
        return !startDate.isAfter(today.plusDays(1));
    }

    /**
     * 判断是否需要订单更新状态为已完成
     * */
    public static boolean isFinished(Date end) {
        // 今天日期
        LocalDate today = LocalDate.now();
        // 订单结束租借日期
        LocalDate endDate = end
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return endDate.isBefore(today.minusDays(1));
    }
}
