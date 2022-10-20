package com.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.Result;
import com.entity.RedisData;
import com.entity.Shop;
import com.google.gson.JsonObject;
import com.mapper.ShopMapper;
import com.service.IShopService;
import com.utils.RedisSpinLock;
import com.utils.StringRedisTemplate2;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisSpinLock redisSpinLock;

    @Resource
    private StringRedisTemplate2 stringRedisTemplate2;

    @Override
    public Result queryShopByid(Long id) {
        //解决缓存穿透的查询
        // Shop shop = queryByIdWithPassThrough(id);

        //使用永不过期来解决逻辑过期
        Shop shop = queryWithNeverExpire(id);
        if (shop == null) {
            return Result.fail("查询信息不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 用于解决缓存穿透 ------- 在缓存中存入空值
     * @param id
     * @return
     */
    public Shop queryByIdWithPassThrough(Long id) {
        //先从缓存中查询商品信息
        Map shopMap = stringRedisTemplate.opsForHash()
                .entries(CACHE_SHOP_KEY + id);
        //缓存命中
        if (!shopMap.isEmpty()) {
            //命中空数据，返回错误信息
            if (shopMap.get("NULL") != null) {
                return null;
            } else {
                //命中真实数据，返回
                Shop shopCache = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
                return shopCache;
            }
        }
        //缓存没有命中，从mysql中查询
        Shop shop = getById(id);
        //mysql中没查到,将空值存入缓存
        if (shop == null) {
            stringRedisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, "NULL", "");
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CATCH_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }
        //mysql中查到了，保存到缓存中在返回
        Map<String, Object> shopMapCache = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create().ignoreNullValue().setFieldValueEditor((fieldKey, fieldValue) -> {
                    if (fieldValue != null) {
                        return fieldValue.toString();
                    }
                    return null;
                }));
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, shopMapCache);
        stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CATCH_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }


    /**
     * 使用互斥锁解决缓存击穿问题 ----- 为缓存中的数据设置过期时间
     * 先从缓存中查询数据，数据不存在时，需要我们从数据库中同步数据
     * 在同步数据时需要使用互斥锁，防止大量线程同时进行缓存重建，
     * 为抢夺到互斥锁的线程会等待其它线程完成缓存的同步
     * @param id
     * @return
     */
    public Shop queryByIdWithMutex(Long id) {
        //先从缓存中查询商品信息
        Map shopMap = stringRedisTemplate.opsForHash()
                .entries(CACHE_SHOP_KEY + id);
        //缓存命中
        if (!shopMap.isEmpty()) {
            Shop shopCache = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
            return shopCache;
        }
        //互斥锁锁住
        boolean isLock = redisSpinLock.lock(SHOP_SPINLOCK_KEY + id);
        //加锁失败，睡眠一段时间后继续
        try {
            if (!isLock) {
                Thread.sleep(50);
                return queryByIdWithMutex(id);
            }
            //缓存没有命中，从mysql中查询
            Shop shop = getById(id);
            //mysql中查到了，保存到缓存中在返回
            Map<String, Object> shopMapCache = BeanUtil.beanToMap(shop, new HashMap<>(),
                    CopyOptions.create().ignoreNullValue().setFieldValueEditor((fieldKey, fieldValue) -> {
                        if (fieldValue != null) {
                            return fieldValue.toString();
                        }
                        return null;
                    }));
            stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, shopMapCache);
            stringRedisTemplate.expire(CACHE_SHOP_KEY + id, CATCH_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            //解锁
            redisSpinLock.unLock(SHOP_SPINLOCK_KEY + id);
        }
    }

    public Shop queryWithNeverExpire(Long id) {
        Shop shop = stringRedisTemplate2.<Shop, Long>getWithLogicalExpire(CACHE_SHOP_KEY + id, id, 10L, Shop.class, new Function<Long, Shop>() {
            @Override
            public Shop apply(Long id) {
                return getById(id);
            }
        });
        return shop;
    }


    public void rebuildShopCache(Long id, Long expireSeconds) throws RuntimeException {
        //从mysql读取数据
        Shop shop = getById(id);
        if (shop == null) {
            throw new RuntimeException();
        }
        //将数据封装成RedisData
        RedisData<Shop> shopCache = new RedisData<>(LocalDateTime.now().plusSeconds(expireSeconds), shop);
        //写入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopCache));
    }

    @Override
    @Transactional
    public void updateShop(Shop shop) {
        //更新数据库
        updateById(shop);
        Long id = shop.getId();
        if (id == null) {
            Result.fail("id不能为空！");
        }
        //将shop对象转换为map
        Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create().setFieldValueEditor((fieldKey, fieldValue) -> {
                    if (fieldValue != null) {
                        return fieldValue.toString();
                    }
                    return null;
                }));
        //更新缓存
        stringRedisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + shop.getId(), shopMap);
        stringRedisTemplate.expire(CACHE_SHOP_KEY + shop.getId(), CATCH_SHOP_TTL, TimeUnit.MINUTES);
    }
}
