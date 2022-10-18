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

    //UUID是为了防止不同服务器上出现相同的线程名
    private static final String THREAD_FLAG_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript UNLOCK_SCRIPT;

    static{
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
    }


    public boolean lock(String key){
        //线程唯一标识
        String threadFlag = THREAD_FLAG_PREFIX + Thread.currentThread().getName();
        Boolean isLock = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + key,threadFlag,60, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isLock);
    }

    public boolean tryLock(String key){
        //线程唯一标识
        String threadFlag = THREAD_FLAG_PREFIX + Thread.currentThread().getName();
        stringRedisTemplate.opsForHash().putIfAbsent(KEY_PREFIX + key,threadFlag,1);

        return true;
    }


    public void unLock(String key){
        //线程唯一标识
        String threadFlag = THREAD_FLAG_PREFIX + Thread.currentThread().getName();
        List<String> keyList = new LinkedList<>();
        keyList.add(KEY_PREFIX + key);
        stringRedisTemplate.execute(UNLOCK_SCRIPT,keyList,threadFlag);
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
