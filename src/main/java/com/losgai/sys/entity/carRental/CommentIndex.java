package com.losgai.sys.entity.carRental;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class CommentIndex {
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
    @Schema(description = "热度评分-当前即为点赞数")
    private Integer hotScore;

    @Schema(description="创建时间")
    private Date createTime;
    @Schema(description="更新时间")
    private Date updateTime;
    @Schema(description="逻辑删除：0=正常，1=已删除")
    private Integer deleted = 0;
}
