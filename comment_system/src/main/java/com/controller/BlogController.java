package com.controller;



import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dto.Result;
import com.dto.UserDTO;
import com.entity.Blog;
import com.service.IBlogService;
import com.service.IUserService;
import com.utils.SystemConstants;
import com.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {

        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }


    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }


    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable Long id){
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable Long id){
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询某一用户的blog信息
     * @param userId 用户id
     * @param current 页码
     */
    @GetMapping("/of/user")
    public Result queryUserBlog(@RequestParam("id") Long userId, @RequestParam("current") Integer current ){
        return blogService.queryUserBlog(userId,current);
    }

    /**
     * 查询关注用户的Blog
     */
    @GetMapping("/of/follow")
    public Result queryFollowBlog(@RequestParam("lastId") Long lastId,
                                  @RequestParam(value = "offset",defaultValue = "0") Integer offset){
        return blogService.queryFollowBlog(lastId,offset);
    }


}
