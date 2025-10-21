package com.losgai.sys.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 后台订单数据显示vo
 */
@Data
public class ShowOrderVo {

    /**
     * 订单id
     * */
    private Long id;

    /**
     * 用户id
     * */
    private Long userId;

    /**
     * 用户名
     * */
    private String username;

    /**
     * 车辆id
     * */
    private Long carId;

    /**
     * 车辆名称
     * */
    private String carName;

    /**
     * 车辆图片
     * */
    private String carImage;

    /**
     * 起租时间
     * */
    private Date startTime;

    /**
     * 结束时间
     * */
    private Date endTime;

    /**
     * 订单总额(人民币元)
     */
    private BigDecimal price;

    /**
     * 地址
     * */
    private String address;

    /**
     * 订单状态：0=新建/待支付，1=已支付，2=租赁中，3=已完成，4=已取消
     */
    private Integer status;

    /**
     * 订单评分0-10，计入车辆均分
     */
    private Integer score;

    /**
     * 创建时间
     * */
    private Date createTime;

    /**
     * 更新时间
     * */
    private Date updateTime;



}