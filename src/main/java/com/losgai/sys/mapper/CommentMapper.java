package com.losgai.sys.mapper;

import com.losgai.sys.entity.carRental.Comment;
import com.losgai.sys.vo.CommentVo;
import com.losgai.sys.vo.TopCommentVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* @author miesme
* @description 针对表【comment(订单信息表)】的数据库操作Mapper
* @createDate 2025-10-14 12:52:33
* @Entity generator.domain.Comment
*/
@Mapper
public interface CommentMapper {

    int deleteByPrimaryKey(Long id);

    int insert(Comment record);

    int insertSelective(Comment record);

    Comment selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Comment record);

    int updateByPrimaryKey(Comment record);

    List<TopCommentVo> query(String keyWord);

    List<TopCommentVo> queryVoByCarIdWithLimit(Long carId);

    List<TopCommentVo> queryVoByCarId(Long carId);

    List<CommentVo> queryVoByIds(List<Long> ids,Integer limit);

    List<CommentVo> loadReplyByCommentId(Long id);

    List<CommentVo> queryVoById(Long id);

    void syncLikeCount(Long commentId, Long likeCount);

    Set<Long> queryCarIdsByCommentIds(Set<Long> commentIds);

    void batchUpdateLikeCount(@Param("commentLikeCounts") Map<Long, Long> commentLikeCounts);
}
