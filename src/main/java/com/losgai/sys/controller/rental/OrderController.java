package com.losgai.sys.controller.rental;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.service.rental.CarService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/order")
@Slf4j
public class OrderController {

    private final CarService carService;

    @SaCheckRole("admin")
    @PostMapping("/admin/add")
    @Tag(name = "管理员新增订单",description = "新增订单")
    public Result<String> add(@RequestBody @Valid Car car) {
//        ResultCodeEnum codeEnum = carService.add(car);
//        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
//            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
//        }
        return Result.success("添加成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/update")
    @Tag(name = "管理员更新订单",description = "更新订单")
    public Result<String> update(@RequestBody @Valid Car car) {
//        ResultCodeEnum codeEnum = carService.update(car);
//        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
//            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
//        }
        return Result.success("更新成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/delete")
    @Tag(name = "管理员删除订单",description = "删除订单")
    public Result<String> delete(@RequestParam Long id) {
//        ResultCodeEnum codeEnum = carService.delete(id);
//        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
//            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
//        }
        return Result.success("删除成功");
    }

//    @SaCheckRole("admin")
//    @GetMapping("/admin/list")
//    @Tag(name = "获取所有订单信息", description = "分页获取当前所有订单信息")
//    public Result<List<Car>> query(
//            @RequestParam(required = false) String keyWord,
//            @RequestParam(defaultValue = "1") int pageNum,
//            @RequestParam(defaultValue = "10") int pageSize) {
//        // 开启分页
//        PageHelper.startPage(pageNum, pageSize);
//        // 执行查询
//        List<Car> list = carService.query(keyWord);
//        // 获取分页信息
//        PageInfo<Car> pageInfo = new PageInfo<>(list);
//        // 使用自定义分页返回方法
//        return Result.page(list, pageInfo.getTotal());
//    }

}
