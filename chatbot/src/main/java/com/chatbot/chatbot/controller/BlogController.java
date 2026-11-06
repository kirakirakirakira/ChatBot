package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.BlogPostSummaryVO;
import com.chatbot.chatbot.dto.BlogPostVO;
import com.chatbot.chatbot.dto.PageVO;
import com.chatbot.chatbot.dto.SaveBlogPostRequest;
import com.chatbot.chatbot.service.BlogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 博客接口。登录由全局 AuthInterceptor 保证，这里不写权限判断：
 * 归属校验（只能改 / 删自己的）在 BlogService，发布策略在 BlogPolicy。
 */
@RestController
@RequestMapping("/api/blog")
public class BlogController {

    private final BlogService blogService;

    public BlogController(BlogService blogService) {
        this.blogService = blogService;
    }

    /** 已发布文章列表，所有登录用户可见。page 从 0 起，size 默认 10、上限 100。 */
    @GetMapping("/posts")
    public PageVO<BlogPostSummaryVO> list(@RequestParam(name = "page", required = false) Integer page,
                                          @RequestParam(name = "size", required = false) Integer size) {
        return blogService.listPublished(page, size);
    }

    /** 详情。未发布的文章只有作者本人能打开，其他人 404。 */
    @GetMapping("/posts/{id}")
    public BlogPostVO detail(@PathVariable Long id, CurrentUser user) {
        return blogService.detail(id, user);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public BlogPostVO create(@Valid @RequestBody SaveBlogPostRequest request, CurrentUser user) {
        return blogService.create(user, request);
    }

    /** 编辑自己的文章。别人的文章一律 404，不是 403。 */
    @PutMapping("/posts/{id}")
    public BlogPostVO update(@PathVariable Long id,
                             @Valid @RequestBody SaveBlogPostRequest request,
                             CurrentUser user) {
        return blogService.update(id, user, request);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, CurrentUser user) {
        blogService.delete(id, user);
    }

    /** 我的文章：所有状态（含以后可能出现的待审 / 驳回 / 下架），只返回自己的。 */
    @GetMapping("/mine")
    public PageVO<BlogPostSummaryVO> mine(@RequestParam(name = "page", required = false) Integer page,
                                          @RequestParam(name = "size", required = false) Integer size,
                                          CurrentUser user) {
        return blogService.listMine(user, page, size);
    }
}