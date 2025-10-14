package com.losgai.sys.mapper;


import com.losgai.sys.entity.carRental.Car;

/**
* @author miesme
* @description 针对表【car(车辆信息表)】的数据库操作Mapper
* @createDate 2025-10-14 12:52:33
* @Entity generator.domain.Car
*/
public interface CarMapper {

    int deleteByPrimaryKey(Long id);

    int insert(Car record);

    int insertSelective(Car record);

    Car selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Car record);

    int updateByPrimaryKey(Car record);

}
