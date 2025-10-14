package com.losgai.sys.mapper;

import com.losgai.sys.entity.carRental.RentalOrder;

/**
* @author miesme
* @description 针对表【rental_order(订单信息表)】的数据库操作Mapper
* @createDate 2025-10-14 12:52:33
* @Entity generator.domain.RentalOrder
*/
public interface RentalOrderMapper {

    int deleteByPrimaryKey(Long id);

    int insert(RentalOrder record);

    int insertSelective(RentalOrder record);

    RentalOrder selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(RentalOrder record);

    int updateByPrimaryKey(RentalOrder record);

}
