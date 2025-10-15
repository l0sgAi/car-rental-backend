package com.losgai.sys.service.sys;

import com.losgai.sys.dto.LoginDto;
import com.losgai.sys.entity.sys.User;
import com.losgai.sys.enums.ResultCodeEnum;

import java.util.List;

public interface UserService {

    ResultCodeEnum doLogin(LoginDto loginDto);

    ResultCodeEnum doRegister(User user);

    User getUserInfo();

    Boolean add(User user);

    void update(User user);

    List<User> queryByKeyWord(String keyWord);
}
