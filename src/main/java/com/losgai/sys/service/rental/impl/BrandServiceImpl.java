package com.losgai.sys.service.rental.impl;

import com.losgai.sys.entity.carRental.Brand;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.mapper.BrandMapper;
import com.losgai.sys.service.rental.BrandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandServiceImpl implements BrandService {

    private final BrandMapper brandMapper;

    @Override
    public List<Brand> queryByKeyWord(String keyWord) {
        return brandMapper.queryByKeyWord(keyWord);
    }
}
