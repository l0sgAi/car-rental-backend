package com.losgai.sys.vo;

import com.losgai.sys.entity.carRental.Car;
import lombok.Data;

import java.util.Date;
import java.util.TreeMap;

/**
 * 下单vo
 */
@Data
public class OrderVo {

    /**
     * 车辆信息
     * */
    private Car car;

    /**
     * 不可租时间段，用TreeMap保存
     * */
    private TreeMap<Date, Date> rentTime;

}