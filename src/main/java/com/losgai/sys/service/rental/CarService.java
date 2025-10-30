package com.losgai.sys.service.rental;

import cn.hutool.db.PageResult;
import com.losgai.sys.common.sys.ESPageResult;
import com.losgai.sys.dto.CarSearchPageParam;
import com.losgai.sys.dto.CarSearchParam;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;

import java.io.IOException;
import java.util.List;

public interface CarService {

    List<Car> query(String keyWord, Integer status);

    List<Car> globalQuery(CarSearchParam carSearchParam);

    ResultCodeEnum add(Car car);

    ResultCodeEnum update(Car car);

    ResultCodeEnum delete(Long id);

    ResultCodeEnum up();

    boolean insertESDocBatch(String indexName,List<Car> cars) throws IOException;

    boolean insertESDoc(String indexName,Car car) throws IOException;

    boolean updateESDoc(String indexName, Car car) throws IOException;

    boolean deleteESDoc(String indexName, String docId) throws IOException;

    Car getCarById(Long id);

    ESPageResult<Car> globalQueryWithPage(CarSearchPageParam carSearchParam);
}
