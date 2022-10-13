package com.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWords {

    //2022-01-01 00：00的UTC时间
    private static final long BIT_UTC = 1640995200;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWords(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long createId(String key){
        LocalDateTime now = LocalDateTime.now();
        //当前日期
        String date = now.format(DateTimeFormatter.ofPattern(":yyyy:MM:dd"));
        long current = now.toEpochSecond(ZoneOffset.UTC) - BIT_UTC;
        //从redis中获取自增值
        Long count = stringRedisTemplate.opsForValue().increment("incr:" + key + date);
        //进行数字拼接
        return current << 32 | count;
    }

}
