package com.losgai.sys.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 评论数据传输对象，支持树形结构
 */
@Data
public class TopCommentVo {
    /**
     * ID
     */
    private Long id;
    
    /**
     * 对应用户ID
     */
    private Long userId;

    /**
     * 评论用户名
     * */
    private String username;

    /**
     * 评论用户头像
     * */
    private String avatar;
    
    /**
     * 对应车辆ID
     */
    private Long carId;

    /**
     * 对应车辆名称
     */
    private String carName;
    
    /**
     * 父级评论id,默认0即为顶级评论
     */
    private Long parentCommentId;
    
    /**
     * 回复评论id,默认0即非回复评论
     */
    private Long followCommentId;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 点赞数量
     */
    private Integer likeCount = 0;

    /**
     * 评分(只有租户有)
     */
    private Integer score;

    @Schema(description="评论图片URL列表(JSON数组)")
    private String extraImages;
    
    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 是否点赞过 1-点赞过 0-未点赞过 默认0
     */
    private Integer liked = 0;
    
    /**
     * 子评论列表
     */
    private List<CommentVo> children;

    public CommentVo toChild(){
         CommentVo vo = new CommentVo();
         vo.setId(this.getId());
         vo.setUserId(this.getUserId());
         vo.setUsername(this.getUsername());
         vo.setAvatar(this.getAvatar());
         vo.setCarId(this.getCarId());
         vo.setCarName(this.getCarName());
         vo.setParentCommentId(this.getParentCommentId());
         vo.setFollowCommentId(this.getFollowCommentId());
         vo.setContent(this.getContent());
         vo.setLikeCount(this.getLikeCount());
         vo.setScore(this.getScore());
         vo.setExtraImages(this.getExtraImages());
         vo.setCreateTime(this.getCreateTime());
         vo.setLiked(this.getLiked());
         return vo;
    }

}