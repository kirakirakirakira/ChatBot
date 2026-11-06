package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.dto.BlogPostSummaryView;
import com.chatbot.chatbot.entity.PostStatus;
import com.chatbot.chatbot.repository.BlogPostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 搜索站内已发布博客。只读，不扫正文，不返回别人的草稿。 */
@Component
public class SearchBlogTool implements Tool {

    private final BlogPostRepository blogPostRepository;

    public SearchBlogTool(BlogPostRepository blogPostRepository) {
        this.blogPostRepository = blogPostRepository;
    }

    @Override
    public String name() {
        return "search_blog";
    }

    @Override
    public String label() {
        return "搜索站内博客";
    }

    @Override
    public String description() {
        return "当用户想查找站内已发布博客、需要文章列表或站内资料时调用。"
                + "不要用于通用知识问答、实时新闻或站外搜索。"
                + "参数 query 必填，是搜索关键词；limit 默认 5，最大 20。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.<String, Object>of(
                "type", "string",
                "description", "搜索关键词，例如 SSE、Spring Boot"));
        properties.put("limit", Map.<String, Object>of(
                "type", "integer",
                "description", "返回条数，默认 5，最大 20",
                "minimum", 1,
                "maximum", 20));
        return Map.<String, Object>of(
                "type", "object",
                "properties", properties,
                "required", List.of("query"));
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments) {
        String query = arguments.path("query").isTextual()
                ? arguments.path("query").asText().strip() : "";
        if (query.isEmpty()) {
            return ToolResult.error("query 不能为空");
        }
        int limit = arguments.path("limit").isNumber() ? arguments.path("limit").asInt() : 5;
        limit = Math.max(1, Math.min(20, limit));

        List<BlogPostSummaryView> rows = blogPostRepository.searchPublished(
                query, PostStatus.PUBLISHED, PageRequest.of(0, limit));
        List<Map<String, Object>> items = new ArrayList<>();
        for (BlogPostSummaryView row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("title", row.getTitle());
            item.put("summary", row.getSummary() == null ? "" : row.getSummary());
            item.put("authorName", row.getAuthorName());
            item.put("publishedAt", row.getPublishedAt() == null ? "" : row.getPublishedAt().toString());
            item.put("url", "/blog/" + row.getId());
            items.add(item);
        }
        if (items.isEmpty()) {
            return ToolResult.ok("没有找到与「" + query + "」相关的站内博客", Map.of("query", query, "items", items));
        }
        return ToolResult.ok("找到 " + items.size() + " 篇与「" + query + "」相关的站内博客",
                Map.of("query", query, "items", items));
    }
}
