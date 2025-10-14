package com.losgai.sys.entity.carRental;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
* 订单信息表
* @TableName comment
*/
@Data
public class Comment implements Serializable {

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
    * 父级评论id,默认0即为顶级评论
    */
    @NotNull(message="[父级评论id,默认0即为顶级评论]不能为空")
    private Long parentCommentId;
    /**
    * 回复评论id,默认0即非回复评论
    */
    @NotNull(message="[回复评论id,默认0即非回复评论]不能为空")
    private Long followCommentId;
    /**
    * 评论内容
    */
    @Size(max= 1024,message="编码长度不能超过1024")
    @Length(max= 1024,message="编码长度不能超过1,024")
    private String content;
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
