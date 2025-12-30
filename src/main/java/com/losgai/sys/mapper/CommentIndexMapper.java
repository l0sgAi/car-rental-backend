package com.losgai.sys.mapper;


import com.losgai.sys.dto.CommentHeatDto;
import com.losgai.sys.dto.CommentIndexDto;
import com.losgai.sys.entity.carRental.CommentIndex;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author miesme
* @description 针对表【comment_index(评论索引表)】的数据库操作Mapper
* @createDate 2025-12-27 13:46:08
* @Entity generator.domain.CommentIndex
*/
@Mapper
public interface CommentIndexMapper {

    int deleteByPrimaryKey(Long id);

    int insert(CommentIndex record);

    int insertSelective(CommentIndex record);

    CommentIndex selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentIndex record);

    int updateByPrimaryKey(CommentIndex record);

    void batchUpdateLikeCountMap(List<CommentIndex> list);

    void batchUpdateHeat(List<CommentHeatDto> heatDtoList);

    List<CommentIndexDto> queryByCarIdWithLimit(Long carId,Integer limit);

    List<CommentIndexDto> queryReplyWithLimit(Long parentCommentId);

    List<CommentIndexDto> queryByCarId(Long carId);
}
