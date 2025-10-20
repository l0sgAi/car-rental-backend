package com.losgai.sys.entity.carRental;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
* 点赞信息表
* @TableName like
*/
@Data
public class Like implements Serializable {

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
    * 对应评论ID
    */
    @NotNull(message="[对应评论ID]不能为空")
    private Long commentId;

    @NotNull(message="[创建时间]不能为空")
    private Date createTime;

    @NotNull(message="[是否撤回点赞]不能为空")
    private Integer isFallback;

    public Like(Long id, Long userId, Long commentId) {
        this.id = id;
        this.userId = userId;
        this.commentId = commentId;
    }
}
