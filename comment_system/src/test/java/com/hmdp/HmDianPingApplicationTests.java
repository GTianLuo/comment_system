package com.hmdp;

import com.service.EmailService;
import com.utils.RedisIdWords;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

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

    public void testRedissonLock() throws InterruptedException {
        RLock lock = redissonClient.getLock("key");
        lock.tryLock(1,10,TimeUnit.SECONDS);
    }
}
