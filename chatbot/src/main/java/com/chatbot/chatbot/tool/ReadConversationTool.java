package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.service.HistorySearchService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取某个会话的一段上下文。和 search_my_history 的分工：
 * 前者负责「找到是哪几句」，这个负责「把那一段完整看一遍」，避免模型只看一句片段就下结论。
 * <p>
 * 归属校验完全复用 ConversationService.requireOwned：不是自己的会话一律 404。
 */
@Component
public class ReadConversationTool implements Tool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 40;

    private final HistorySearchService historySearchService;

    public ReadConversationTool(HistorySearchService historySearchService) {
        this.historySearchService = historySearchService;
    }

    @Override
    public String name() {
        return "read_conversation";
    }

    @Override
    public String label() {
        return "读取历史对话上下文";
    }

    @Override
    public String description() {
        return "当已经知道某个会话 id、需要展开那一段对话的上下文时调用（通常跟在 search_my_history 或 list_my_conversations 后面）。"
                + "用户明确要求打开 / 读取某个会话时必须实际调用，不要凭上下文猜测内容。"
                + "参数 conversationId 必填；aroundMessageId 可选，传了就以这条消息为中心取前后各一半，"
                + "不传则取最新 limit 条；limit 默认 20，最大 40。"
                + "只能读当前登录用户自己的会话，别人的会话会返回不存在。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conversationId", Map.<String, Object>of(
                "type", "integer",
                "description", "会话 id，来自 search_my_history 或 list_my_conversations 的返回"));
        properties.put("aroundMessageId", Map.<String, Object>of(
                "type", "integer",
                "description", "可选：以这条消息为中心读取上下文；不传则读最新消息"));
        properties.put("limit", Map.<String, Object>of(
                "type", "integer",
                "description", "最多读取多少条消息，默认 20，最大 40",
                "minimum", 2,
                "maximum", MAX_LIMIT));
        return Map.<String, Object>of(
                "type", "object",
                "properties", properties,
                "required", List.of("conversationId"));
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode arguments) {
        if (!arguments.path("conversationId").isNumber()) {
            return ToolResult.error("conversationId 必须是数字");
        }
        long conversationId = arguments.path("conversationId").asLong();
        Long around = arguments.path("aroundMessageId").isNumber()
                ? arguments.path("aroundMessageId").asLong() : null;
        int limit = arguments.path("limit").isNumber() ? arguments.path("limit").asInt() : DEFAULT_LIMIT;
        limit = Math.max(2, Math.min(MAX_LIMIT, limit));

        try {
            HistorySearchService.ConversationWindow window = historySearchService.readConversation(
                    conversationId, context.user(), around, limit);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Message m : window.messages()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("messageId", m.getId());
                item.put("role", m.getRole().name().toLowerCase());
                item.put("content", HistorySearchService.clipForModel(
                        m.getContent(), HistorySearchService.clipLimit()));
                item.put("createdAt", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("conversationId", window.conversationId());
            data.put("title", window.title());
            data.put("items", items);
            data.put("url", around == null
                    ? "/chat?c=" + conversationId
                    : "/chat?c=" + conversationId + "&m=" + around);
            return ToolResult.ok("读取会话《" + window.title() + "》的 " + items.size() + " 条消息", data);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ToolResult.error("会话不存在或无权访问: " + conversationId);
            }
            return ToolResult.error("读取会话失败: " + e.getReason());
        }
    }
}
