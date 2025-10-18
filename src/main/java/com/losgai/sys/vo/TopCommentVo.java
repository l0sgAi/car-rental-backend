package com.losgai.sys.vo;

import com.losgai.sys.entity.carRental.Comment;
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
     * 评论用户类型 0-未租过 1-租户 2-管理员
     * */
    private Integer userType;
    
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
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 子评论列表
     */
    private List<CommentVo> children;

    /**
     * 将Comment实体转换为CommentDTO
     */
    public static TopCommentVo fromEntity(Comment comment) {
        TopCommentVo dto = new TopCommentVo();
        dto.setId(comment.getId());
        dto.setUserId(comment.getUserId());
        dto.setCarId(comment.getCarId());
        dto.setParentCommentId(comment.getParentCommentId());
        dto.setFollowCommentId(comment.getFollowCommentId());
        dto.setContent(comment.getContent());
        dto.setLikeCount(comment.getLikeCount());
        dto.setCreateTime(comment.getCreateTime());
        return dto;
    }
}