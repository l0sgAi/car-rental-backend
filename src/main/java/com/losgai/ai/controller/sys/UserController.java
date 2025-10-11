package com.losgai.ai.controller.sys;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.losgai.ai.common.sys.Result;
import com.losgai.ai.dto.LoginDto;
import com.losgai.ai.entity.sys.User;
import com.losgai.ai.enums.ResultCodeEnum;
import com.losgai.ai.service.sys.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
    public Result<SaTokenInfo> doRegister(@RequestBody User user) {
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

}
