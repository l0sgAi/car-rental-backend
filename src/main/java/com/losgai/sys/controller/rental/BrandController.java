package com.losgai.sys.controller.rental;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.entity.carRental.Brand;
import com.losgai.sys.service.rental.BrandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/brand")
@Slf4j
public class BrandController {

    private final BrandService brandService;

    @GetMapping("/admin/list")
    @Tag(name = "获取所有品牌信息", description = "分页获取当前所有品牌信息")
    public Result<List<Brand>> query(
            @RequestParam(required = false) String keyWord,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<Brand> list = brandService.queryByKeyWord(keyWord);
        // 获取分页信息
        PageInfo<Brand> pageInfo = new PageInfo<>(list);
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

}
