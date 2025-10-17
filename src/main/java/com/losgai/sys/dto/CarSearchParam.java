package com.losgai.sys.dto;

import lombok.Data;

/**
 * ES 车辆信息文档
 * */
@Data
public class CarSearchParam {

    // 搜索关键字
    private String keyWord;

    // 新：筛选选项,null时不进行筛选
    private String carType;
    private String powerType;
    private Long brandId;
    private Integer minimPrice;
    private Integer maxPrice;
    
    // 排序选项-0正序 1倒序
    private Integer avgScore;
    private Integer hotScore;
    private Integer fuelConsumption;
    private Integer dailyRent;
    private Integer seat;

    // 分页参数
    private Integer pageNum = 1;    // 页码，默认第1页
    private Integer pageSize = 12;  // 每页12条

}