package com.losgai.sys.service.sys;

import com.losgai.sys.dto.LoginDto;
import com.losgai.sys.entity.sys.User;
import com.losgai.sys.enums.ResultCodeEnum;

public interface UserService {

    ResultCodeEnum doLogin(LoginDto loginDto);

    ResultCodeEnum doRegister(User user);

    User getUserInfo();
}
