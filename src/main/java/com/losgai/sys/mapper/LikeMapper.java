package com.losgai.sys.mapper;

import com.losgai.sys.dto.CommentLikeCountDto;
import com.losgai.sys.entity.carRental.Like;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author miesme
* @description 针对表【like(点赞信息表)】的数据库操作Mapper
* @createDate 2025-10-20 11:04:32
* @Entity generator.domain.Like
*/
@Mapper
public interface LikeMapper {

    int deleteByPrimaryKey(Long id);

    int insert(Like record);

    int insertSelective(Like record);

    Like selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Like record);

    int updateByPrimaryKey(Like record);

    List<Long> listUserIdsByCommentId(Long commentId);

    void batchInsert(List<Like> newLikes);

    int deleteByCommentId(Long commentId,Long userId);

    List<Like> listActiveLikesByCommentIds(@Param("commentIds") Set<Long> commentIds);

    void batchSoftDelete(List<Like> likesToSoftDelete);

    Long existByUserIdAndCommentId(Long userId, Long commentId);

    Integer countByCommentId(Long commentId);

    List<Like> selectLatestLikes(Long userId, Integer likeLimit);

    /**
     * 批量查询评论点赞数
     * @param commentIds 评论ID列表
     * @return 包含ID和数量的对象列表
     */
    List<CommentLikeCountDto> selectCountsBatch(@Param("commentIds") List<Long> commentIds);
}
