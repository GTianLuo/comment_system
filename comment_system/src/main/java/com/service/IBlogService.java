package com.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dto.Result;
import com.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result likeBlog(Long id);

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result queryBlogLikes(Long id);

    Result queryUserBlog(Long userId, Integer current);

    Result saveBlog(Blog blog);

    Result queryFollowBlog(Long lastId,Integer offset);
}
