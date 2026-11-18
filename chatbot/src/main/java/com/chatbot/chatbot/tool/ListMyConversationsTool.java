package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.service.HistorySearchService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按标题列出当前用户的会话。用途只有一个：用户只记得主题、不记得原话时，
 * 先缩小候选范围，再交给 read_conversation 展开。刻意不带消息正文。
 */
@Component
public class ListMyConversationsTool implements Tool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final HistorySearchService historySearchService;

    public ListMyConversationsTool(HistorySearchService historySearchService) {
        this.historySearchService = historySearchService;
    }

    @Override
    public String name() {
        return "list_my_conversations";
    }

    @Override
    public String label() {
        return "列出我的历史会话";
    }

    @Override
    public String description() {
        return "当用户要求「列出我的会话 / 找找是哪个会话」或只记得大体主题时调用，按标题列出当前登录用户自己的会话。"
                + "用户明确要求列会话时必须实际调用，不要凭上下文猜测。"
                + "参数 keyword 可选，用来按标题过滤；limit 默认 10，最大 30。"
                + "这个方法只返回会话标题和摘要，不含消息正文；要看内容请再用 read_conversation。"
                + "不要用它回答与用户历史无关的问题。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.<String, Object>of(
                "type", "string",
                "description", "可选：按标题过滤的关键词"));
        properties.put("limit", Map.<String, Object>of(
                "type", "integer",
                "description", "返回条数，默认 10，最大 30",
                "minimum", 1,
                "maximum", MAX_LIMIT));
        return Map.<String, Object>of(
                "type", "object",
                "properties", properties);
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments) {
        String keyword = arguments.path("keyword").isTextual()
                ? arguments.path("keyword").asText().strip() : "";
        int limit = arguments.path("limit").isNumber() ? arguments.path("limit").asInt() : DEFAULT_LIMIT;
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));

        List<Conversation> rows = historySearchService.listConversations(
                context.user().id(), keyword, limit);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (Conversation c : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", c.getId());
            item.put("title", c.getTitle());
            item.put("summary", c.getSummary() == null
                    ? "" : HistorySearchService.clipForModel(c.getSummary(), 160));
            item.put("updatedAt", c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
            item.put("url", "/chat?c=" + c.getId());
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword);
        data.put("items", items);
        if (items.isEmpty()) {
            return ToolResult.ok(keyword.isEmpty()
                    ? "当前没有任何历史会话"
                    : "没有标题包含「" + keyword + "」的历史会话", data);
        }
        return ToolResult.ok("找到 " + items.size() + " 个历史会话", data);
    }
}
