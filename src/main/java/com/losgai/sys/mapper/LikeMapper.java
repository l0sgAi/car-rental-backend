package com.losgai.sys.mapper;

import com.losgai.sys.entity.carRental.Like;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
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

    void deleteByCommentId(Long commentId);

    List<Like> listActiveLikesByCommentIds(@Param("commentIds") Set<Long> commentIds);

    void batchSoftDelete(List<Like> likesToSoftDelete);
}
