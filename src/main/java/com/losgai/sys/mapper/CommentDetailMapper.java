package com.losgai.sys.mapper;


import com.losgai.sys.entity.carRental.CommentDetail;
import com.losgai.sys.vo.CommentVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author miesme
* @description 针对表【comment_detail(评论详情表)】的数据库操作Mapper
* @createDate 2025-12-27 13:46:08
* @Entity generator.domain.CommentDetail
*/
@Mapper
public interface CommentDetailMapper {

    int deleteByPrimaryKey(Long id);

    int insert(CommentDetail record);

    int insertSelective(CommentDetail record);

    CommentDetail selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(CommentDetail record);

    int updateByPrimaryKey(CommentDetail record);

    List<CommentVo> query(String keyWord);

    List<CommentDetail> selectBatchIds(List<Long> missingIds);
}
