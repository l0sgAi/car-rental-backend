package com.losgai.sys.mapper;

import com.losgai.sys.dto.CommentDto;
import com.losgai.sys.vo.CommentVo;
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

    int insert(CommentDto record);

    int insertSelective(CommentDto record);

    CommentDto selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentDto record);

    int updateByPrimaryKey(CommentDto record);

    List<CommentVo> query(String keyWord);

    List<CommentVo> queryVoByCarIdWithLimit(Long carId);

    List<CommentVo> queryVoByCarId(Long carId);

    List<CommentVo> queryVoByIds(List<Long> ids,Integer limit);

    List<CommentVo> loadReplyByCommentId(Long id);

    List<CommentVo> queryVoById(Long id);

    void syncLikeCount(Long commentId, Long likeCount);

    Set<Long> queryCarIdsByCommentIds(Set<Long> commentIds);

    void batchUpdateLikeCount(@Param("commentLikeCounts") Map<Long, Long> commentLikeCounts);

}
