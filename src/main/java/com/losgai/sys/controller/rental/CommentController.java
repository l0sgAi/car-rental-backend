package com.losgai.sys.controller.rental;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
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
@RequestMapping("/rental/comment")
@Slf4j
public class CommentController {

    private final CarService carService;

    @PostMapping("/admin/add")
    @Tag(name = "新增评论",description = "新增/回复评论")
    public Result<String> add(@RequestBody @Valid Car car) {
//        ResultCodeEnum codeEnum = carService.add(car);
//        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
//            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
//        }
        return Result.success("添加成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/delete")
    @Tag(name = "管理员删除评论",description = "删除评论")
    public Result<String> delete(@RequestParam Long id) {
//        ResultCodeEnum codeEnum = carService.delete(id);
//        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
//            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
//        }
        return Result.success("删除成功");
    }

    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    @Tag(name = "获取所有评论信息", description = "管理员分页获取当前所有评论信息列表")
    public Result<List<Car>> query(
            @RequestParam(required = false) String keyWord,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<Car> list = carService.queryByKeyWord(keyWord);
        // 获取分页信息
        PageInfo<Car> pageInfo = new PageInfo<>(list);
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

}
