package com.losgai.sys.service.rental;

import com.losgai.sys.entity.carRental.Brand;
import com.losgai.sys.entity.carRental.Car;

import java.util.List;

public interface BrandService {
    List<Brand> queryByKeyWord(String keyWord);
}
