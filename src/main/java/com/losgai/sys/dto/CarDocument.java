package com.losgai.sys.dto;

import com.losgai.sys.entity.carRental.Car;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ES 车辆信息文档
 * */
@Data
public class CarDocument implements Serializable {
    
    private Long id;
    
    // 搜索属性
    private String name;
    private String number;
    private String carType;
    private String powerType;
    
    // 排序属性
    private Integer avgScore;
    private Integer hotScore;
    private Integer fuelConsumption;
    private BigDecimal dailyRent;
    private Integer seat;
    
    // 不可搜索和排序的属性
    private Long brandId;
    private String images;
    private Integer status;
    
    // 转换方法
    public static CarDocument fromCar(Car car) {
        CarDocument doc = new CarDocument();
        doc.setId(car.getId());
        doc.setName(car.getName());
        doc.setNumber(car.getNumber());
        doc.setCarType(car.getCarType());
        doc.setPowerType(car.getPowerType());
        doc.setAvgScore(car.getAvgScore());
        doc.setHotScore(car.getHotScore());
        doc.setFuelConsumption(car.getFuelConsumption());
        doc.setDailyRent(car.getDailyRent());
        doc.setSeat(car.getSeat());
        doc.setBrandId(car.getBrandId());
        doc.setImages(car.getImages());
        doc.setStatus(car.getStatus());
        return doc;
    }
}