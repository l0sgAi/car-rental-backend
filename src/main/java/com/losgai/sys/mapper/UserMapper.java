package com.losgai.sys.mapper;

import com.losgai.sys.entity.sys.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Losgai
 * @description 针对表【user(用户信息表)】的数据库操作Mapper
 * @createDate 2025-06-21 16:25:37
 * @Entity generator.entity.User
 */
@Mapper
public interface UserMapper {

    int deleteByPrimaryKey(Long id);

    int deleteOrdersByUserId(Long id);

    int deleteCommentsByUserId(Long id);

    int insert(User record);

    int insertSelective(User record);

    User selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(User record);

    int updateByPrimaryKey(User record);

    /**
     * 通过用户名查询用户
     */
    User selectByUsername(String username);

    /**
     * 通过用户名查询用户是否存在
     */
    int existsByUsername(@Param("idNumber") String idNumber,
                         @Param("phone") String phone,
                         @Param("licenseNumber") String licenseNumber);

    List<User> queryByKeyWord(String keyWord);

}
