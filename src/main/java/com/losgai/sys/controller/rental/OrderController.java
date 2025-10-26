package com.losgai.sys.controller.rental;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.dto.BookingDto;
import com.losgai.sys.entity.sys.User;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.service.rental.OrderService;
import com.losgai.sys.service.sys.UserService;
import com.losgai.sys.vo.OrderVo;
import com.losgai.sys.vo.ShowOrderVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental/order")
@Slf4j
public class OrderController {

    private final OrderService orderService;

    private final UserService userService;

    @GetMapping("/start")
    @Tag(name = "准备下单",description = "进入准备下单页面")
    public Result<OrderVo> start(@RequestParam Long carId) {
        User userInfo = userService.getUserInfo();
        if(StrUtil.isBlank(userInfo.getIdNumber()) || StrUtil.isBlank(userInfo.getLicenseNumber())){
            return Result.error("无有效驾驶证或身份证，请完善个人信息");
        }
        OrderVo orderVo = orderService.getStartOrderVo(carId);
        if (orderVo == null){
            return Result.error("车辆不存在");
        }
        return Result.success(orderVo);
    }

    @PostMapping("/create")
    @Tag(name = "生成订单",description = "用户选择车辆和租车时间段后，生成订单")
    public Result<Long> add(@RequestBody @Valid BookingDto bookingDto) {
        Long orderId = orderService.create(bookingDto);
        if(orderId<=0){
            return Result.error("订单生成失败");
        }
        return Result.success(orderId);
    }

    @PutMapping("/pay")
    @Tag(name = "支付订单",description = "用户支付订单，更新订单状态，返回支付表单页面字符串")
    public Result<String> pay(@RequestParam Long orderId) {
        String payForm = orderService.pay(orderId);
        if (!Objects.equals(payForm, ResultCodeEnum.SUCCESS.getMessage())) {
            return Result.error(payForm);
        }
        return Result.success(payForm);
    }

    @PutMapping("/cancel")
    @Tag(name = "取消订单",description = "用户修改订单状态，取消订单")
    public Result<String> cancel(@RequestParam Long orderId) {
        ResultCodeEnum codeEnum = orderService.cancel(orderId);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("下单成功");
    }

    @PutMapping("/ranking")
    @Tag(name = "订单评分",description = "用户给订单打分0-10")
    public Result<String> ranking(
            @RequestParam Long orderId,
            @RequestParam Integer score
    ) {
        ResultCodeEnum codeEnum = orderService.ranking(orderId,score);
        if (!Objects.equals(codeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())) {
            return Result.info(codeEnum.getCode(),codeEnum.getMessage());
        }
        return Result.success("下单成功");
    }


    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    @Tag(name = "获取所有订单信息", description = "分页获取当前所有订单信息")
    public Result<List<ShowOrderVo>> query(
            @RequestParam(required = false) String keyWord,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        // 解析为Date查询
        Date start = null;
        if(StrUtil.isNotBlank(startDate)){
            Instant instant = Instant.parse(startDate);
            start = Date.from(instant);
        }
        Date end = null;
        if(StrUtil.isNotBlank(endDate)){
            Instant instant = Instant.parse(endDate);
            start = Date.from(instant);
        }
        // 执行条件查询
        List<ShowOrderVo> list = orderService.query(keyWord,start,end,status);
        // 获取分页信息
        PageInfo<ShowOrderVo> pageInfo = new PageInfo<>(list);
        // 清理分页
        PageHelper.clearPage();
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

    @GetMapping("/user/list")
    @Tag(name = "获取所有订单信息", description = "用户分页获取当前所有订单信息")
    public Result<List<ShowOrderVo>> userQuery(
            @RequestParam(required = false) String keyWord,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        // 解析为Date查询
        Date start = null;
        if(StrUtil.isNotBlank(startDate)){
            Instant instant = Instant.parse(startDate);
            start = Date.from(instant);
        }
        Date end = null;
        if(StrUtil.isNotBlank(endDate)){
            Instant instant = Instant.parse(endDate);
            start = Date.from(instant);
        }
        // 执行条件查询
        List<ShowOrderVo> list = orderService.userQuery(keyWord,start,end,status);
        // 获取分页信息
        PageInfo<ShowOrderVo> pageInfo = new PageInfo<>(list);
        // 清理分页
        PageHelper.clearPage();
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

    /**
     * 导出订单台账
     */
    @SaCheckRole("admin")
    @GetMapping("/admin/export")
    @Tag(name = "导出订单台账", description = "导出所有订单台账")
    public void exportOrders(HttpServletResponse response) throws IOException {
        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");

        // 生成文件名：订单台账_导出时间.xlsx
        String fileName = URLEncoder.encode("订单台账_" +
                        new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 调用服务层导出
        orderService.exportOrdersToExcel(response.getOutputStream());
    }

}
