package com.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.dto.UserDTO;
import com.entity.User;
import com.utils.UserHolder;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.utils.RedisConstants.LOGIN_TOKEN_KEY;
import static com.utils.RedisConstants.LOGIN_TOKEN_TTL;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    //拦截器中不能被spring容器注入，因为在springboot在配置拦截器时，是手动创建的
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request
            , HttpServletResponse response, Object handler) throws Exception {
        //从请求头获取token
        String token = (String)request.getHeader("authorization");

        if(StrUtil.isBlank(token)){
            return false;
        }
        //从Redis中通过token获取UserDTO
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(LOGIN_TOKEN_KEY + token);
        if(userMap.isEmpty()){
            return false;
        }
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(user);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response
            , Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
