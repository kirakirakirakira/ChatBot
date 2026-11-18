package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.service.HistorySearchService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨会话搜索当前用户自己的历史消息。
 * <p>
 * 刻意排除当前会话：正在聊的内容本来就在上下文里，再搜回自己会让命中列表全是噪声。
 * 返回 data 给模型判断，ui.citations 给前端渲染引用卡，citations 给后端落库。
 */
@Component
public class SearchMyHistoryTool implements Tool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final HistorySearchService historySearchService;

    public SearchMyHistoryTool(HistorySearchService historySearchService) {
        this.historySearchService = historySearchService;
    }

    @Override
    public String name() {
        return "search_my_history";
    }

    @Override
    public String label() {
        return "搜索我的历史对话";
    }

    @Override
    public String description() {
        return "当用户提到「以前 / 上次 / 之前 / 历史 / 我们聊过 / 我说过」或要找回过去问过的内容时调用。"
                + "只搜索当前登录用户自己的会话，且不含当前会话（当前对话已经在上下文中）。"
                + "参数 query 必填，是关键词或原话片段；limit 默认 5，最大 10。"
                + "不要用于通用知识问答、站内博客搜索或站外搜索。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.<String, Object>of(
                "type", "string",
                "description", "要找回的关键词或原话片段，例如 SSE 断线重连"));
        properties.put("limit", Map.<String, Object>of(
                "type", "integer",
                "description", "返回的消息条数，默认 5，最大 10",
                "minimum", 1,
                "maximum", MAX_LIMIT));
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
        int limit = arguments.path("limit").isNumber() ? arguments.path("limit").asInt() : DEFAULT_LIMIT;
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));

        HistorySearchService.SearchResult result = historySearchService.search(
                context.user().id(), context.conversationId(), query, limit);

        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> conversations = new ArrayList<>();
        List<ToolCitation> citations = new ArrayList<>();

        for (HistorySearchService.MessageHit hit : result.messages()) {
            String role = hit.role() == null ? null : hit.role().toLowerCase();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("messageId", hit.messageId());
            item.put("conversationId", hit.conversationId());
            item.put("conversationTitle", hit.conversationTitle());
            item.put("role", role);
            item.put("snippet", hit.snippet());
            item.put("createdAt", hit.createdAt() == null ? null : hit.createdAt().toString());
            item.put("url", messageUrl(hit.conversationId(), hit.messageId()));
            items.add(item);
            citations.add(new ToolCitation(hit.messageId(), hit.conversationId(), hit.conversationTitle(),
                    role, hit.snippet(), hit.createdAt()));
        }

        for (HistorySearchService.ConversationHit hit : result.conversations()) {
            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("conversationId", hit.conversationId());
            conv.put("title", hit.title());
            conv.put("summary", hit.summary());
            conv.put("updatedAt", hit.updatedAt() == null ? null : hit.updatedAt().toString());
            conv.put("url", conversationUrl(hit.conversationId()));
            conversations.add(conv);
            citations.add(new ToolCitation(null, hit.conversationId(), hit.title(), null,
                    hit.summary(), hit.updatedAt()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("items", items);
        data.put("conversations", conversations);
        // ui 只做展示：卡片需要 key / 跳转参数，正文留在 data 的 snippet 里，不重复塞一份
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("citations", citations.stream().map(SearchMyHistoryTool::toUiCitation).toList());

        if (items.isEmpty() && conversations.isEmpty()) {
            return ToolResult.ok("没有找到与「" + query + "」相关的历史记录", data, ui, List.of());
        }
        String summary = "找到 " + items.size() + " 条与「" + query + "」相关的历史消息";
        if (!conversations.isEmpty()) {
            summary += "，另有 " + conversations.size() + " 个主题相关的会话";
        }
        return ToolResult.ok(summary, data, ui, citations);
    }

    private static Map<String, Object> toUiCitation(ToolCitation c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", c.citedMessageId() == null
                ? "c-" + c.citedConversationId() : "m-" + c.citedMessageId());
        item.put("messageId", c.citedMessageId());
        item.put("conversationId", c.citedConversationId());
        item.put("conversationTitle", c.conversationTitle());
        item.put("role", c.role());
        item.put("snippet", c.snippet());
        item.put("createdAt", c.citedAt() == null ? null : c.citedAt().toString());
        item.put("url", c.citedMessageId() == null
                ? conversationUrl(c.citedConversationId())
                : messageUrl(c.citedConversationId(), c.citedMessageId()));
        return item;
    }

    private static String messageUrl(Long conversationId, Long messageId) {
        return "/chat?c=" + conversationId + "&m=" + messageId;
    }

    private static String conversationUrl(Long conversationId) {
        return "/chat?c=" + conversationId;
    }
}
