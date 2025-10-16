package com.losgai.sys.mapper;

import com.losgai.sys.entity.carRental.Comment;
import org.apache.ibatis.annotations.Mapper;

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

}
