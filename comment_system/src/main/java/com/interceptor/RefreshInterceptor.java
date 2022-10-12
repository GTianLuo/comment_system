package com.interceptor;

import cn.hutool.core.util.StrUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.concurrent.TimeUnit;

import static com.utils.RedisConstants.LOGIN_TOKEN_KEY;
import static com.utils.RedisConstants.LOGIN_TOKEN_TTL;

public class RefreshInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从请求头获取token
        String token = (String)request.getHeader("authorization");
        //token不存在时不需要刷新用户信息的expire
        if(StrUtil.isBlank(token)){
            return true;
        }
        //刷新用户的expire
        stringRedisTemplate.expire(LOGIN_TOKEN_KEY + token,LOGIN_TOKEN_TTL, TimeUnit.MINUTES);
        return true;
    }
}
