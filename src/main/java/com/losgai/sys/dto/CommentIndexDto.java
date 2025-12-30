package com.losgai.sys.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class CommentIndexDto {
    /**
     * ID
     */
    private Long id;
    /**
     * 对应用户ID
     */
    private Long userId;
    @Schema(description = "用户名")
    private String username;
    @Schema(description = "用户头像")
    private String avatar;
    /**
     * 对应车辆ID
     */
    private Long carId;
    /**
     * 父级评论id,默认0即为顶级评论
     */
    private Long parentCommentId;
    /**
     * 回复评论id,默认0即非回复评论
     */
    private Long followCommentId;
    @Schema(description = "回复数")
    private Integer followCount;
    @Schema(description = "热度评分-当前即为点赞数")
    private Integer hotScore;


    @Schema(description="创建时间")
    private Date createTime;
    @Schema(description="更新时间")
    private Date updateTime;
    @Schema(description="逻辑删除：0=正常，1=已删除")
    private Integer deleted = 0;
}
