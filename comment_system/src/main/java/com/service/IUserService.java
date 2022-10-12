package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dto.LoginFormDTO;
import com.dto.Result;
import com.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result logout(String token);

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
