package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dto.Result;
import com.entity.ShopType;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
