package com.losgai.sys.controller.rental;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.db.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.ESPageResult;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.dto.CarSearchPageParam;
import com.losgai.sys.dto.CarSearchParam;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.service.rental.CarService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/car")
@Slf4j
public class CarController {

    private final CarService carService;

    @SaCheckRole("admin")
    @PostMapping("/admin/add")
    @Tag(name = "管理员新增车辆",description = "新增车辆")
    public Result<String> add(@RequestBody @Valid Car car) {
        ResultCodeEnum codeEnum = carService.add(car);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("添加成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/update")
    @Tag(name = "管理员更新车辆",description = "更新车辆")
    public Result<String> update(@RequestBody @Valid Car car) {
        ResultCodeEnum codeEnum = carService.update(car);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("更新成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/delete")
    @Tag(name = "管理员删除车辆",description = "删除车辆")
    public Result<String> delete(@RequestParam Long id) {
        ResultCodeEnum codeEnum = carService.delete(id);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("删除成功");
    }

    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    @Tag(name = "获取所有车辆信息", description = "分页获取当前所有车辆信息")
    public Result<List<Car>> query(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyWord,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<Car> list = carService.query(keyWord,status);
        // 获取分页信息
        PageInfo<Car> pageInfo = new PageInfo<>(list);
        // 清理分页
        PageHelper.clearPage();
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

    @SaCheckRole("admin")
    @PostMapping("/admin/up")
    @Tag(name = "上架车辆", description = "管理员上架所有可租车辆信息")
    public Result<String> up() {
        // 执行查询
        ResultCodeEnum resultCodeEnum = carService.up();
        if (!Objects.equals(resultCodeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(resultCodeEnum.getCode(),resultCodeEnum.getMessage());
        }
        return Result.success("上架成功");
    }

    @GetMapping("/detail/{id}")
    @Tag(name = "获取车辆详细信息", description = "分页获取当前车辆信息")
    public Result<Car> detail(@PathVariable Long id) {
        // 执行查询
        Car car = carService.getCarById(id);
        return Result.success(car);
    }

    @PostMapping("/globalQuery")
    @Tag(name = "获取所有车辆信息", description = "通过查询/筛选获取当前车辆信息")
    public Result<List<Car>> globalQuery(@RequestBody CarSearchParam carSearchParam) {
        return Result.success(carService.globalQuery(carSearchParam));
    }

    @PostMapping("/globalQueryWithPage")
    @Tag(name = "获取所有车辆信息", description = "通过查询/筛选获取当前车辆信息（分页）")
    public Result<ESPageResult<Car>> globalQueryWithPage(@RequestBody CarSearchPageParam carSearchParam) {
        return Result.success(carService.globalQueryWithPage(carSearchParam));
    }

}
