package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import com.entity.VoucherOrder;
import com.service.EmailService;
import com.utils.RedisIdWords;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    public static final String threadName = Thread.currentThread().getName();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private EmailService emailService;

    @Resource
    private RedisIdWords redisIdWords;
    @Test
    public void testString(){
        Object object = 1;
    }

    @Test
    public void testGetId(){
        System.out.println(redisIdWords.createId("1"));
    }

    @Test
    public void testPostEmail(){
        emailService.sendSimpleMail("2985496686@qq.com","美团验证码","您的验证码为：473987，请妥善保管，不要告诉他人！");
    }

    @Test
    public void testRedissonLock() throws InterruptedException {
        RLock lock = redissonClient.getLock("key");
        //第一个参数标识获取锁失败后的最大等待时间，第二个参数锁的过期时间
        lock.tryLock(1,1000,TimeUnit.SECONDS );
        //不传参数时默认第一个参数为-1，表示获取锁失败后不等待，第二个参数默认为30
        //lock.tryLock()
        lock.unlock();
    }

    @Test
    public void testThread(){
        for (int i = 0; i < 10; i++) {
            new Thread(()->{
                System.out.println(threadName);
                System.out.println(Thread.currentThread().getName());
            }).start();
        }
    }

    @Test
    public void testThread2(){
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("lslslslsl");
            }
        });
    }


    @Test
    public void testMq(){
        while (true){
            //从消息队列中读取数据
            List<MapRecord<String, Object, Object>> orderList = stringRedisTemplate.opsForStream().read(Consumer.from("orderGroup", "c1")
                    , StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2))
                    , StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
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
                //voucherOrderHandler(voucherOrder);
                //ACK 确认订单
               // stringRedisTemplate.opsForStream().acknowledge(STREAM_NAME,"orderGroup",recordId);
            } catch (Exception e) {
               // log.error("数据处理异常！",e);
                //数据处理时产生异常
                //handlePendingList();
            }
        }
    }
}
