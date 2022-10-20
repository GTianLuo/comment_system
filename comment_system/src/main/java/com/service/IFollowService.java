package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dto.Result;
import com.entity.Follow;

public interface IFollowService extends IService<Follow> {


    Result followUser(Long followUserId, Boolean isFollow);

    Result hadFollowed(Long followUserId);

    Result queryCommonFollow(Long userId);
}
