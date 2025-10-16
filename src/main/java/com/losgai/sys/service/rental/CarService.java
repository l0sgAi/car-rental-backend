package com.losgai.sys.service.rental;

import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;

import java.util.List;

public interface CarService {

    List<Car> queryByKeyWord(String keyWord);

    List<Car> globalQuery(String keyWord);

    ResultCodeEnum add(Car car);

    ResultCodeEnum update(Car car);

    ResultCodeEnum delete(Long id);
}
