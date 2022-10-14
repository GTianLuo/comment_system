package com.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.Result;
import com.dto.UserDTO;
import com.entity.SeckillVoucher;
import com.entity.Voucher;
import com.entity.VoucherOrder;
import com.mapper.VoucherOrderMapper;
import com.service.ISeckillVoucherService;
import com.service.IVoucherOrderService;
import com.service.IVoucherService;
import com.utils.RedisIdWords;
import com.utils.RedisSpinLock;
import com.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWords redisIdWords;

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    @Resource
    private RedisSpinLock redisSpinLock;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠卷信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断优惠劵秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(voucher.getBeginTime())){
            return Result.fail("秒杀未开始！");
        }
        //判断优惠卷秒杀是否结束
        if (now.isAfter(voucher.getEndTime())){
            return Result.fail("秒杀已经结束！");
        }
        //判断优惠卷库存
        if(voucher.getStock() <= 0){
            return Result.fail("优惠券已被抢空！");
        }
        String key = UserHolder.getUser().getId().toString().intern();
        try {
            boolean isLock = redisSpinLock.lock(key);
            if (!isLock){
                //获取锁失败
                return Result.fail("禁止连续秒杀！");
            }
            return voucherOrderService.executeSeckillVoucher(voucherId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            redisSpinLock.unLock(key);
        }
    }

    /**
     * 秒杀优惠卷
     * @param voucherId 优惠卷id
     */
    @Transactional
    public Result executeSeckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWords.createId("voucher:id:" + voucherId);
        VoucherOrder voucherOrder = new VoucherOrder(orderId,userId,voucherId);
            //执行秒杀
            //更新数据库库存
            Integer count = query().eq("user_id", userId).eq("voucher_id",voucherId).count();
            if(count > 0){
                return Result.fail("重复秒杀郭天宇！");
            }
            boolean updateSuccess = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock",0)
                    .update();
            if(!updateSuccess){
                return Result.fail("优惠卷已被抢空！");
            }
            //保存订单
            save(voucherOrder);
            return Result.ok();
    }
}
