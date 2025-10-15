package com.losgai.sys.controller.sys;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.losgai.sys.common.sys.Result;
import com.losgai.sys.dto.LoginDto;
import com.losgai.sys.entity.ai.AiConfig;
import com.losgai.sys.entity.sys.User;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.service.sys.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sys/user")
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/doLogin")
    @Tag(name = "用户登录",description = "用户单点登录")
    public Result<SaTokenInfo> doLogin(@RequestBody LoginDto loginDto) {
        ResultCodeEnum resultCodeEnum = userService.doLogin(loginDto);
        if(Objects.equals(resultCodeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())){
            // 获取令牌
            SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
            // 返回给前端
            return Result.success(tokenInfo);
        }
        return Result.error(resultCodeEnum.getMessage());
    }

    @PostMapping("/auth/doRegister")
    @Tag(name = "用户注册",description = "用户单点注册")
    public Result<SaTokenInfo> doRegister(@RequestBody @Valid User user) {
        ResultCodeEnum resultCodeEnum = userService.doRegister(user);
        if(Objects.equals(resultCodeEnum.getCode(), ResultCodeEnum.SUCCESS.getCode())){
            // 获取令牌
            SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
            // 返回给前端
            return Result.success(tokenInfo);
        }
        return Result.error(resultCodeEnum.getMessage());
    }

    @GetMapping("/getUserInfo")
    @Tag(name = "获取用户信息", description = "获取当前登录用户信息")
    public Result<User> getUserInfo() {
        if (StpUtil.isLogin()) { // 判断是否登录
            // 从Session中获取用 户信息（如果登录时已保存）
            User user = userService.getUserInfo();
            return Result.success(user);
        }
        return Result.error("用户未登录");
    }

    // 当前应用独自注销 (不退出其它应用)
    @RequestMapping("/doLogout")
    @Tag(name = "用户注销", description = "单端独立注销")
    public Result<String> logoutByAlone() {
        StpUtil.logout();
        return Result.success("注销成功");
    }

    @SaCheckRole("admin")
    @PostMapping("/admin/add")
    @Tag(name = "管理员新增用户",description = "新增用户")
    public Result<String> add(@RequestBody @Valid User user) {
        Boolean success = userService.add(user);
        if (!success) {
            return Result.error("添加失败");
        }
        return Result.success("添加成功");
    }

    @SaCheckRole("admin")
    @PutMapping("/admin/update")
    @Tag(name = "管理员更新用户",description = "更新用户")
    public Result<String> update(@RequestBody @Valid User user) {
        userService.update(user);
        return Result.success("更新成功");
    }

    @SaCheckRole("user")
    @PutMapping("/user/update")
    @Tag(name = "用户自更新个人信息",description = "更新个人信息")
    public Result<String> updateSelf(@RequestBody @Valid User user) {
        user.setId(StpUtil.getLoginIdAsLong());
        userService.update(user);
        return Result.success("更新成功");
    }

    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    @Tag(name = "获取所有用户信息", description = "获取当前所有用户信息")
    public Result<List<User>> query(
            @RequestParam(required = false) String keyWord,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 开启分页
        PageHelper.startPage(pageNum, pageSize);
        // 执行查询
        List<User> list = userService.queryByKeyWord(keyWord);
        // 获取分页信息
        PageInfo<User> pageInfo = new PageInfo<>(list);
        // 使用自定义分页返回方法
        return Result.page(list, pageInfo.getTotal());
    }

}
