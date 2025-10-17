package com.losgai.sys.service.rental;

import com.losgai.sys.dto.CarSearchParam;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;

import java.io.IOException;
import java.util.List;

public interface CarService {

    List<Car> queryByKeyWord(String keyWord);

    List<Car> globalQuery(CarSearchParam carSearchParam);

    ResultCodeEnum add(Car car);

    ResultCodeEnum update(Car car);

    ResultCodeEnum delete(Long id);

    ResultCodeEnum up();

    boolean insertESDocBatch(String indexName,List<Car> cars) throws IOException;

    boolean insertESDoc(String indexName,Car car) throws IOException;

    boolean updateESDoc(String indexName, Car car) throws IOException;
}
