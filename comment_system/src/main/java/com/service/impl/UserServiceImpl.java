package com.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.LoginFormDTO;
import com.dto.Result;
import com.dto.UserDTO;
import com.entity.User;
import com.mapper.UserMapper;
import com.service.EmailService;
import com.service.IUserService;
import com.utils.EmailUtil;
import com.utils.RegexUtils;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.utils.EmailUtil.EMAIL_SUBJECT;
import static com.utils.RedisConstants.*;
import static com.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private EmailService emailService;
    @Override
    public Result sendCode(String email, HttpSession session) {
        //手机号校验
        if(RegexUtils.isEmailInvalid(email)){
            return Result.fail("错误邮箱！");
        }
        //生成随机验证码
        String code = RandomUtil.randomNumbers(6);
        //发送验证码
        log.debug(code);
        System.out.println(code);
        emailService.sendSimpleMail(email,EMAIL_SUBJECT, EmailUtil.format(code));
        //将验证码保存在redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + email , code,LOGIN_CODE_TTL, TimeUnit.SECONDS);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        //校验手机号码
        String email = loginForm.getEmail();
        if(RegexUtils.isEmailInvalid(email)){
            return Result.fail("错误邮箱！");
        }
        //从Redis中获取code
        String cacheCode =  stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        String code = loginForm.getCode();
        //校验验证码
        if(!code.equals(cacheCode)){
            return Result.fail("无效验证码");
        }
        //查找用户信息
        User user = query().eq("phone", email).one();
        //用户不存在，创建用户
        if(user == null){
            user = createNewUser(email);
            save(user);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().ignoreNullValue().setFieldValueEditor((filedName,fildValue)->
                    fildValue.toString()
                ));
        //生成随机的token，作为存储userMap的key，存储到Redis
        UUID token = UUID.randomUUID();
        stringRedisTemplate.opsForHash().putAll(LOGIN_TOKEN_KEY + token,userMap);
        stringRedisTemplate.expire(LOGIN_TOKEN_KEY + token,LOGIN_TOKEN_TTL,TimeUnit.MINUTES);
        return  Result.ok(token.toString());

    }

    @Override
    public Result logout(String token) {
        HashOperations<String, Object, Object> hashOps = stringRedisTemplate.opsForHash();
        //删除redis中的user
        hashOps.delete(LOGIN_TOKEN_KEY + token, hashOps.keys(LOGIN_TOKEN_KEY + token).toArray());
        return Result.ok();
    }

    private User createNewUser(String phone) {
        User user = new User(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        return user;
    }


    @Override
    public Result queryUser(Long userId) {
        User user = getById(userId);
        if (user != null){
            return Result.ok(user);
        }else{
            return Result.fail("未查询到该用户消息！");
        }
    }
}
