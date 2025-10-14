package com.losgai.sys.entity.sys;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户信息表
 * @TableName user
 */
@Data
public class User implements Serializable {

    /**
     * ID
     */
    @NotNull(message="[ID]不能为空")
    private Long id;
    /**
     * 用户名
     */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String username;
    /**
     * 加密后的密码
     */
    @Size(max= 512,message="编码长度不能超过512")
    @Length(max= 512,message="编码长度不能超过512")
    private String password;
    /**
     * 身份证号
     */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String idNumber;
    /**
     * 驾驶证编号
     */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String licenseNumber;
    /**
     * 初次领驾驶证日期
     */
    private Date licenseDate;
    /**
     * 手机号
     */
    @Size(max= 20,message="编码长度不能超过20")
    @Length(max= 20,message="编码长度不能超过20")
    private String phone;
    /**
     * 实名姓名
     */
    @Size(max= 50,message="编码长度不能超过50")
    @Length(max= 50,message="编码长度不能超过50")
    private String realName;
    /**
     * 头像链接
     */
    @Size(max= 255,message="编码长度不能超过255")
    @Length(max= 255,message="编码长度不能超过255")
    private String avatarUrl;
    /**
     * 性别：0=未知，1=男，2=女
     */
    private Integer gender;
    /**
     * 出生日期
     */
    private Date birthdate;
    /**
     * 状态：0=禁用，1=启用
     */
    @NotNull(message="[状态：0=禁用，1=启用]不能为空")
    private Integer status;
    /**
     * 角色id 0用户1管理员
     */
    @NotNull(message="[角色id 0用户1管理员]不能为空")
    private Long role;
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
