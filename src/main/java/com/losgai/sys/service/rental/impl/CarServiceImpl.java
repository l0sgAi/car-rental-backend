package com.losgai.sys.service.rental.impl;

import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.CarMapper;
import com.losgai.sys.service.rental.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarServiceImpl implements CarService {

    private final CarMapper carMapper;
    @Override
    public List<Car> queryByKeyWord(String keyWord) {
        return carMapper.queryByKeyWord(keyWord);
    }

    @Override
    public List<Car> globalQuery(String keyWord) {
        return List.of();
    }

    @Override
    public ResultCodeEnum add(Car car) {
        carMapper.insert(car);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public ResultCodeEnum update(Car car) {
        carMapper.updateByPrimaryKeySelective(car);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Transactional
    public ResultCodeEnum delete(Long id) {
        carMapper.deleteByPrimaryKey(id);
        carMapper.deleteOrdersByCarId(id);
        carMapper.deleteCommentsByCarId(id);
        return ResultCodeEnum.SUCCESS;
    }
}
