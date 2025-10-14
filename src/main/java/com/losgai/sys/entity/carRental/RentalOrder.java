package com.losgai.sys.entity.carRental;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
* 订单信息表
* @TableName rental_order
*/
@Data
public class RentalOrder implements Serializable {

    /**
    * ID
    */
    @NotNull(message="[ID]不能为空")
    private Long id;
    /**
    * 对应用户ID
    */
    @NotNull(message="[对应用户ID]不能为空")
    private Long userId;
    /**
    * 对应车辆ID
    */
    @NotNull(message="[对应车辆ID]不能为空")
    private Long carId;
    /**
    * 车辆起租日期
    */
    @NotNull(message="[车辆起租日期]不能为空")
    private Date startRentalTime;
    /**
    * 车辆还车日期
    */
    @NotNull(message="[车辆还车日期]不能为空")
    private Date endRentalTime;
    /**
    * 订单总额(人民币元)
    */
    @NotNull(message="[订单总额(人民币元)]不能为空")
    private BigDecimal price;
    /**
    * 订单状态：0=新建/待支付，1=已支付，2=租赁中，3=已完成，4=已取消
    */
    @NotNull(message="[订单状态：0=新建/待支付，1=已支付，2=租赁中，3=已完成，4=已取消]不能为空")
    private Integer status;
    /**
    * 订单评分0-10，计入车辆均分
    */
    private Integer score;
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
