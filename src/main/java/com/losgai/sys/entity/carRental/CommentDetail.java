package com.losgai.sys.entity.carRental;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
public class CommentDetail {
    @Schema(description="ID")
    private Long id;
    @Schema(description="索引ID")
    private Long indexId;
    @Schema(description="评论内容")
    private String content;
    @Schema(description="点赞数")
    private Integer likeCount;
    @Schema(description="近期订单评分")
    private Integer score;
    @Schema(description="评论图片URL列表(JSON数组)")
    private Object extraImages;
    @Schema(description="创建时间")
    private Date createTime;
    @Schema(description="更新时间")
    private Date updateTime;
    @Schema(description="逻辑删除：0=正常，1=已删除")
    private Integer deleted=0;
}
