package com.controller;


import com.dto.Result;
import com.dto.UserDTO;
import com.entity.Follow;
import com.service.IFollowService;
import com.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;


    /**
     *  关注或者取关用户
     * @param followUserId  关注或取关的用户id
     * @param isFollow  关注/取关
     */
    @PutMapping("/{followUserId}/{isFollow}")
    public Result followUser(@PathVariable Long followUserId,@PathVariable Boolean isFollow){
        return  followService.followUser(followUserId,isFollow);
    }

    /**
     *  判断用户是否已经被关注
     * @param followUserId 被判断的用户
     */
    @GetMapping("/or/not/{followUserId}")
    public Result hadFollowed(@PathVariable Long followUserId){
        return followService.hadFollowed(followUserId);
    }

    /**
     * 获取共同关注
     * @param userId 用户id
     */
    @GetMapping("/common/{userId}")
    public Result queryCommonFollow(@PathVariable Long userId){
        return followService.queryCommonFollow(userId);
    }

}
