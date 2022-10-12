package com.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.entity.RedisData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


@Component
public class StringRedisTemplate2 {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisSpinLock redisSpinLock;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将对象obj转为json字符串并序列化存储到Redis
     *
     * @param value   存储对象
     * @param timeout 过期时间
     */
    public void set(String key, Object value, Long timeout, TimeUnit unit) {

        String objJson = JSONUtil.toJsonStr(value);

        stringRedisTemplate.opsForValue().set(key, objJson, timeout, unit);

    }

    /**
     * @param obj        存储对象
     * @param expireTime 逻辑过期时间，单位为秒
     */
    public void setWithLogicalExpire(String key, Object obj, LocalDateTime expireTime) {
        //封装对象
        RedisData redisData = new RedisData(expireTime, obj);
        //转换为json
        String redisDataJson = JSONUtil.toJsonStr(redisData);
        //存储
        stringRedisTemplate.opsForValue().set(key, redisDataJson);
    }


    public <T, ID> T getWithPassThrough(String key, ID id, Class<T> type, Long timeout,
                                        TimeUnit unit, Function<ID, T> function) {
        //查询缓存
        String objCache = stringRedisTemplate.opsForValue().get(key);
        //缓存命中直接返回
        if (StrUtil.isNotBlank(objCache)) {
            T t = JSONUtil.toBean(objCache, type);
        }
        //缓存命中失败，查询数据库
        T value = function.apply(id);
        //数据库命中将查询结构加入缓存
        if (value != null) {
            set(key, value, timeout, unit);
        } else {
            //数据库没有命中，将null存入缓存
            set(key, "", 10L, TimeUnit.SECONDS);
        }
        return value;
    }


    public <T, ID> T getWithLogicalExpire(String key, ID id, Long timeout, Class<T> type, Function<ID, T> function) {
        //查询缓存
        String redisDataCache = stringRedisTemplate.opsForValue().get(key);
        RedisData redisData = null;
        T value = null;
        if (redisDataCache != null) {
            //缓存命中，将查询结果反序列化
            redisData = JSONUtil.toBean(redisDataCache, RedisData.class);
            JSONObject data = (JSONObject) redisData.getData();
            value = JSONUtil.toBean(data, type);
            //检查缓存是否过期
            if (redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                //缓存没有过期
                return value;
            }
        }
        //缓存过期
        //加锁
        boolean lock = redisSpinLock.lock(key);
        if (lock) {
            try {
                if (redisData != null && redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                    //在获取锁过程中缓存已经被更新
                    return value;
                }
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    reBuild(key, id, timeout, function);
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                redisSpinLock.unLock(key);
            }
        }
        return value;
    }

    private <T, ID> void reBuild(String key, ID id, Long timeout, Function<ID, T> function) throws RuntimeException {
        //查找数据库
        T value = function.apply(id);
        if (value == null) {
            throw new RuntimeException("无法重建不存在数据！");
        }
        //将value封装为RedisData
        RedisData<T> redisData = new RedisData<>(LocalDateTime.now().plusSeconds(timeout), value);
        //将RedisData转为json
        String redisDataJson = JSONUtil.toJsonStr(redisData);
        //存入缓存
        stringRedisTemplate.opsForValue().set(key, redisDataJson);
    }


}
