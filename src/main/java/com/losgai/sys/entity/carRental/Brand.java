package com.losgai.sys.entity.carRental;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
* 品牌
* @TableName brand
*/
@Data
public class Brand implements Serializable {

    /**
    * 品牌id
    */
    @NotNull(message="[品牌id]不能为空")
    private Long id;
    /**
    * 品牌名
    */
    private String name;
    /**
    * 品牌logo地址
    */
    @Size(max= 2000,message="编码长度不能超过2000")
    @Length(max= 2000,message="编码长度不能超过2,000")
    private String logo;
    /**
    * 介绍
    */
    private String descript;
    /**
    * 逻辑删除：0=正常，1=已删除
    */
    @NotNull(message="[逻辑删除：0=正常，1=已删除]不能为空")
    private Integer deleted;

}
