package com.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisSpinLock {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private static final String THREAD_FLAG = UUID.randomUUID().toString(true) + "-"
            +Thread.currentThread().getName();

    private static final DefaultRedisScript UNLOCK_SCRIPT;

    static{
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
    }

    public boolean lock(String key){
        //获取当前线程名
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key,THREAD_FLAG, 60, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isLock);
    }


    public void unLock(String key){
        List<String> keyList = new LinkedList<>();
        keyList.add(KEY_PREFIX + key);
        stringRedisTemplate.execute(UNLOCK_SCRIPT,keyList,THREAD_FLAG);
    }



    /**
    public void unLock(String key){
        //先判断这个锁是否是当前线程持有
        String s = stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (THREAD_FLAG.equals(s)) {
            stringRedisTemplate.delete(KEY_PREFIX + key);
        }
    }
     **/
}
