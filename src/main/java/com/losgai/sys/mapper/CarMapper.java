package com.losgai.sys.mapper;


import com.losgai.sys.dto.OrderScoreDto;
import com.losgai.sys.entity.carRental.Car;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
* @author miesme
* @description 针对表【car(车辆信息表)】的数据库操作Mapper
* @createDate 2025-10-14 12:52:33
* @Entity generator.domain.Car
*/
@Mapper
public interface CarMapper {

    int deleteByPrimaryKey(Long id);

    int deleteOrdersByCarId(Long id);

    int deleteCommentsByCarId(Long id);

    int insert(Car record);

    int insertSelective(Car record);

    Car selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(Car record);

    int updateByPrimaryKey(Car record);

    List<Car> query(String keyWord, Integer status);

    List<Car> getAllCanRentCars();

    void updateHotScore(Long carId, Integer hot);

    void calculateCarAvgScoreBatch();
}
