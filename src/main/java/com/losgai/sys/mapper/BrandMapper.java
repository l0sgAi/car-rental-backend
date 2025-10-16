package com.losgai.sys.mapper;

import com.losgai.sys.entity.carRental.Brand;
import com.losgai.sys.entity.carRental.Car;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author miesme
* @description 针对表【brand(品牌)】的数据库操作Mapper
* @createDate 2025-10-16 16:03:52
* @Entity generator.domain.Brand
*/
@Mapper
public interface BrandMapper {

    int deleteByPrimaryKey(Long id);

    int insert(Brand record);

    int insertSelective(Brand record);

    Brand selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Brand record);

    int updateByPrimaryKey(Brand record);

    List<Brand> queryByKeyWord(String keyWord);
}
