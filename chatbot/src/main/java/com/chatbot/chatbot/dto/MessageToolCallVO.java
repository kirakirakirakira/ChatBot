package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.AgentToolCall;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 刷新 / 切会话后回放的一步工具调用（GET messages 里随助手消息返回）。
 * <p>
 * 字段和 SSE 的 tool_call / tool_result 对齐：前端可以直接映射成 AgentStep，
 * 只是不会带 ui（引用卡单独走 MessageVO.citations）。
 * 刻意不返回 result_json：get_blog_post 的完整正文可能很大，时间线只需要 summary。
 */
public record MessageToolCallVO(
        String callId,
        String tool,
        String label,
        Map<String, Object> args,
        String status,
        String summary,
        Integer durationMs) {

    public static MessageToolCallVO from(AgentToolCall row, ObjectMapper objectMapper) {
        Map<String, Object> args = Map.of();
        String json = row.getArgumentsJson();
        if (json != null && !json.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
                if (parsed != null) {
                    args = parsed;
                }
            } catch (Exception ignored) {
                // 参数不是合法 JSON 时返回空对象：时间线仍能显示工具名 / 结果 / 耗时
            }
        }
        return new MessageToolCallVO(
                row.getCallId(),
                row.getToolName(),
                row.getToolLabel() == null ? row.getToolName() : row.getToolLabel(),
                args,
                row.getStatus(),
                row.getResultSummary(),
                row.getDurationMs());
    }
}
