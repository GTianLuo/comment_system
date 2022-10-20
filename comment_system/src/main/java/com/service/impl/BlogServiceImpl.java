package com.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dto.Result;
import com.dto.ScrollResult;
import com.dto.UserDTO;
import com.entity.Blog;
import com.entity.Follow;
import com.entity.User;
import com.mapper.BlogMapper;
import com.service.IBlogService;
import com.service.IFollowService;
import com.service.IUserService;
import com.utils.SystemConstants;
import com.utils.UserHolder;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;

import static com.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.utils.RedisConstants.FOLLOW_BLOG_KEY;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Override
    public Result likeBlog(Long id) {

        Long userId = UserHolder.getUser().getId();
        //判断用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        //用户未点赞，将redis中的isLike置为true
        if(score == null){
            //数据库点赞数量加一
            boolean success = update().setSql("liked = liked  + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,userId.toString(),System.currentTimeMillis());
            }
            return Result.ok();
        }
        //用户已经点赞
        //数据库点赞数量-1
        boolean success = update().setSql("liked = liked  - 1").eq("id", id).update();
        if (success) {
            //redis删除点赞记录
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY  +id,userId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    private void isLikedBlog(Blog blog){
        UserDTO user = UserHolder.getUser();
        if(user != null){
            Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), user.getId().toString());
            blog.setIsLike(score != null);
        }
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
        });
        return Result.ok(records);
    }


    private void queryBlogUser(Blog blog){
        User user = userService.getById(blog.getUserId());
        if(user != null){
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());
            isLikedBlog(blog);
        }
    }

    @Override
    public Result queryBlogLikes(Long id) {
        Set<String> records = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        List<Long> userList = new LinkedList<>();
        List<UserDTO> userDTOList = new LinkedList<>();
        UserDTO userDTO = null;
        for(String userId:records){
            userList.add(Long.parseLong(userId));
        }
        if (!userList.isEmpty()) {
            String usersStr = StrUtil.join(",", userList);
            List<User> users = userService
                    .query()
                    .in("id", userList)
                    .last("ORDER BY FIELD(id," + usersStr + ")")
                    .list();
            for(User user :users){
                userDTOList.add(BeanUtil.copyProperties(user,UserDTO.class));
            }
        }
        return Result.ok(userDTOList);
    }

    @Override
    public Result queryUserBlog(Long userId, Integer current) {

        Page<Blog> userBlogs = query().eq("user_id", userId).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = userBlogs.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if(save){
            //将用户消息推送至粉丝邮箱中
            //查询粉丝消息
            List<Follow> follows = followService
                    .query()
                    .eq("follow_user_id", user.getId().toString())
                    .select("user_id")
                    .list();
            for(Follow follow: follows){
                stringRedisTemplate.opsForZSet()
                        .add(FOLLOW_BLOG_KEY + follow.getUserId(),blog.getId().toString(),System.currentTimeMillis());
            }
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryFollowBlog(Long lastId,Integer offset) {
        UserDTO user = UserHolder.getUser();
        //查询用户被推送的blog信息
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(FOLLOW_BLOG_KEY + user.getId()
                , 0, lastId, offset, 2);

        List<Long> ids = new ArrayList<>(typedTuples.size());
        Double minScore = null;
        offset = 1;
        for(ZSetOperations.TypedTuple<String> typedTuple: typedTuples){
            if(typedTuple == null) break;
            ids.add(Long.parseLong(typedTuple.getValue()));
            if(minScore == null){
                minScore = typedTuple.getScore();
            }else if(minScore == typedTuple.getScore()){
                offset++;
            }else{
                minScore = typedTuple.getScore();
                offset = 1;
            }
        }
        List<Blog> blogs = null;
        if (!ids.isEmpty()) {
            String idsStr = StrUtil.join(",", ids);
            blogs = query().
                    in("id", ids).
                    last("ORDER BY FIELD(id," + idsStr + ")")
                    .list();
        }
        for(Blog blog : blogs){
            queryBlogUser(blog);
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minScore.longValue());
        scrollResult.setOffset(offset);

        return Result.ok(scrollResult);
    }
}