package com.losgai.sys.entity.carRental;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
    * 支付宝订单编号
    */
    private String tradeNo;
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
     * 取还车地址
     * */
    @NotBlank(message = "[地址]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @Length(max= 255,message="编码长度不能超过255")
    private String address;
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
