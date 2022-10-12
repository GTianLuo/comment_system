package com.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.Result;
import com.entity.ShopType;
import com.mapper.ShopTypeMapper;
import com.service.IShopTypeService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private RedisTemplate<String,ShopType> redisTemplate;
    @Override
    public Result queryTypeList() {
        //先从缓存中查询
        List<ShopType> shopTypeList = redisTemplate.opsForList().range("cache:shopType", 0, -1);
        //缓存命中，直接返回
        if (!shopTypeList.isEmpty()) {
            return Result.ok(shopTypeList);
        }
        //缓存没有命中，从mysql中查询
        shopTypeList = query().orderByAsc("sort").list();
        //mysql中没查到，返回错误信息
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("错误查询！");
        }
        //mysql查到了，将查询信息保存到缓存中再返回
        redisTemplate.opsForList().rightPushAll("cache:shopType",shopTypeList);
        return Result.ok(shopTypeList);
    }
}
