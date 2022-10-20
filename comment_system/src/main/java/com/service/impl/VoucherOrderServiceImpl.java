package com.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.Result;
import com.entity.SeckillVoucher;
import com.entity.VoucherOrder;
import com.mapper.VoucherOrderMapper;
import com.service.ISeckillVoucherService;
import com.service.IVoucherOrderService;
import com.utils.RedisIdWords;
import com.utils.RedisSpinLock;
import com.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript SECKILL_SCRIPT;

    static{
        SECKILL_SCRIPT = new DefaultRedisScript();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckillVoucher.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    /**private static BlockingQueue<VoucherOrder> seckillQueue = new ArrayBlockingQueue<>(1024*1024);
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //从阻塞队列获取订单信息
                    VoucherOrder voucher = seckillQueue.take();
                    //进行同步数据库的操作
                    voucherOrderHandler(voucher);
                } catch (Exception e) {
                    log.error("处理订单异常！",e);
                    e.printStackTrace();
                }
            }
        }
    }**/

    private static final  String STREAM_NAME = "stream.orders";
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                //从消息队列中读取数据
                List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(Consumer.from("orderGroup", "c1")
                        , StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2))
                        , StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed()));
                //未读到数据重试
                if(orderList == null || orderList.isEmpty()){
                    continue;
                }
                try {
                    //处理数据
                    MapRecord<String, Object, Object> orderRecord = orderList.get(0);
                    RecordId recordId = orderRecord.getId();
                    Map<Object, Object> orderMap = orderRecord.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMap, new VoucherOrder(), false);
                    System.out.println(voucherOrder);
                    voucherOrderHandler(voucherOrder);
                    //ACK 确认订单
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME,"orderGroup",recordId);
                } catch (Exception e) {
                    log.error("数据处理异常！",e);
                    //数据处理时产生异常
                    handlePendingList();
                }
            }
        }
    }

    public void handlePendingList(){
        while (true){
            //从消息队列中读取数据
            List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(Consumer.from("orderGroup", "c1")
                    , StreamReadOptions.empty().count(1)
                    , StreamOffset.create(STREAM_NAME, ReadOffset.from("0")));
            //未读到数据重试
            if(orderList == null || orderList.isEmpty()){
                break;
            }
            try {
                //处理数据
                MapRecord<String, Object, Object> orderRecord = orderList.get(0);
                RecordId recordId = orderRecord.getId();
                Map<Object, Object> orderMap = orderRecord.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMap, new VoucherOrder(), false);
                voucherOrderHandler(voucherOrder);
                //ACK 确认订单
                stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME,"orderGroup",recordId);
            } catch (Exception e) {
                log.error("apending-list处理异常",e);
                continue;
            }
        }
    }

    public void voucherOrderHandler(VoucherOrder voucherOrder)throws RuntimeException{
        Long voucherId = voucherOrder.getVoucherId();

        //更新数据库库存
        boolean updateSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!updateSuccess){
            throw new RuntimeException("数据库更新异常！");
        }
        //保存订单
        save(voucherOrder);
    }

    public Result seckillVoucherOldVersion(Long voucherId) {
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
        //因为Lang底层的toString方法是通过new的方式创建的字符串，所以这里不能直接锁字符串
        //inter方法会直接返回字符串在常量池中的地址
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
                return Result.fail("重复秒杀优惠劵！");
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



    public Result seckillVoucher(Long voucherId){
        String userId = UserHolder.getUser().getId().toString();
        String orderId = redisIdWords.createId("voucher:id:" + voucherId).toString();
        //在缓存中进行秒杀业务
        List<String> keyList = new ArrayList<>();
        Long rnt = (Long) stringRedisTemplate.execute(SECKILL_SCRIPT, keyList, userId,voucherId.toString(),orderId);
        //处理秒杀结果
        if(rnt != 0){
            return rnt == 2 ? Result.fail("重复秒杀！"): Result.fail("库存不足！");
        }
        return Result.ok();
    }
}
