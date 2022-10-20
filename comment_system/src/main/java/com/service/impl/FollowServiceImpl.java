package com.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.Result;
import com.dto.UserDTO;
import com.entity.Follow;
import com.entity.User;
import com.mapper.FollowMapper;
import com.service.IFollowService;
import com.service.IUserService;
import com.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static com.utils.RedisConstants.USER_FOLLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result followUser(Long followUserId, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(user.getId());
            follow.setFollowUserId(followUserId);
            boolean save = save(follow);
            if(save){
                //将关注消息保存到Redis中
                stringRedisTemplate.opsForSet().add(USER_FOLLLOW_KEY + user.getId(),followUserId.toString());
            }
        }else{
            //取关
            boolean remove = remove(new QueryWrapper<Follow>()
                    .eq("user_id", user.getId())
                    .eq("follow_user_id", followUserId));

            if(remove){
                stringRedisTemplate.opsForSet().remove(USER_FOLLLOW_KEY + user.getId(),followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result hadFollowed(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        Integer count = query().eq("user_id", user.getId()).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result queryCommonFollow(Long userId) {
        String myId = UserHolder.getUser().getId().toString();
        Set<String> ids = stringRedisTemplate.opsForSet()
                .intersect(USER_FOLLLOW_KEY + myId, USER_FOLLLOW_KEY + userId);
        if (ids == null ||ids.isEmpty()){
            return Result.ok();
        }
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTOS = new ArrayList<>();
        for(User user :users){
            userDTOS.add(BeanUtil.copyProperties(user,UserDTO.class));
        }
        return Result.ok(userDTOS);
    }
}
