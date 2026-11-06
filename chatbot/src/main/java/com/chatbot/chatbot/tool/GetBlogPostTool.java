package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.dto.BlogPostVO;
import com.chatbot.chatbot.service.BlogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 读取一篇站内博客。可见性完全复用 BlogService.detail：未发布只有作者本人能读。 */
@Component
public class GetBlogPostTool implements Tool {

    private final BlogService blogService;

    public GetBlogPostTool(BlogService blogService) {
        this.blogService = blogService;
    }

    @Override
    public String name() {
        return "get_blog_post";
    }

    @Override
    public String label() {
        return "读取博客文章";
    }

    @Override
    public String description() {
        return "当用户要查看某一篇站内博客的正文、或需要基于某篇文章回答时调用。"
                + "参数 id 必填。未发布文章只有作者本人能读取，其他人会得到不存在。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.<String, Object>of(
                "type", "integer",
                "description", "博客文章 id"));
        return Map.<String, Object>of(
                "type", "object",
                "properties", properties,
                "required", List.of("id"));
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments) {
        if (!arguments.path("id").isNumber()) {
            return ToolResult.error("id 必须是数字");
        }
        long id = arguments.path("id").asLong();
        try {
            BlogPostVO post = blogService.detail(id, context.user());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", post.id());
            data.put("title", post.title());
            data.put("summary", post.summary() == null ? "" : post.summary());
            data.put("content", post.content());
            data.put("authorName", post.authorName());
            data.put("url", "/blog/" + post.id());
            return ToolResult.ok("读取文章《" + post.title() + "》成功", data);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ToolResult.error("文章不存在或无权访问: " + id);
            }
            return ToolResult.error("读取文章失败: " + e.getReason());
        }
    }
}
