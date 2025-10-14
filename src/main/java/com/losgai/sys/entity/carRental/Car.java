package com.losgai.sys.entity.carRental;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
* 车辆信息表
* @TableName car
*/
@Data
public class Car implements Serializable {

    /**
    * ID
    */
    @NotNull(message="[ID]不能为空")
    private Long id;

    /**
     * ID
     */
    @NotNull(message="[brandId]不能为空")
    private Long brandId;
    /**
    * 车辆名
    */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String name;
    /**
    * 车牌号
    */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String number;
    /**
    * 最小租赁天数
    */
    @NotNull(message="[最小租赁天数]不能为空")
    private Integer minRentalDays;
    /**
    * 日租金(人民币元)
    */
    @NotNull(message="[日租金(人民币元)]不能为空")
    private BigDecimal dailyRent;
    /**
    * 车型
    */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String carType;
    /**
    * 动力类型
    */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String powerType;
    /**
    * 车辆购买日期
    */
    private Date purchaseTime;
    /**
    * 马力
    */
    private Integer horsepower;
    /**
    * 最大扭矩
    */
    private Integer torque;
    /**
    * 百公里油耗(L/100km)
    */
    private Integer fuelConsumption;
    /**
    * 理论续航km
    */
    private Integer endurance;
    /**
    * 描述
    */
    @Size(max= 1536,message="编码长度不能超过1536")
    @Length(max= 1536,message="编码长度不能超过1,536")
    private String description;
    /**
    * 尺寸，长×宽×高
    */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String size;
    /**
    * 座位数
    */
    private Integer seat;
    /**
    * 车重(kg)
    */
    private Integer weight;
    /**
    * 储物容积(L)
    */
    private Integer volume;
    /**
    * 百公里加速(s)
    */
    private BigDecimal acceleration;
    /**
    * 图片url列表,逗号分隔，最多9张图片
    */
    @Size(max= 1536,message="编码长度不能超过1536")
    @Length(max= 1536,message="编码长度不能超过1,536")
    private String images;
    /**
    * 车辆状态：0=正常，1=不可租
    */
    @NotNull(message="[车辆状态：0=正常，1=不可租]不能为空")
    private Integer status;
    /**
    * 热度评分
    */
    @NotNull(message="[热度评分]不能为空")
    private Integer hotScore;
    /**
    * 车辆用户平均评分
    */
    @NotNull(message="[车辆用户平均评分]不能为空")
    private Integer avgScore;
    /**
    * 创建时间
    */
    @NotNull(message="[创建时间]不能为空")
    private Date createTime;
    /**
    * 更新时间
    */
    @NotNull(message="[更新时间]不能为空")
    private Date updateTime;
    /**
    * 逻辑删除：0=正常，1=已删除
    */
    @NotNull(message="[逻辑删除：0=正常，1=已删除]不能为空")
    private Integer deleted;

}
