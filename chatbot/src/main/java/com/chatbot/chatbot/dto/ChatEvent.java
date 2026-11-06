package com.chatbot.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * SSE 推送给前端的事件。NON_NULL 让 null 字段不出现在 JSON 里，前端按 type 取对应字段：
 * {"type":"reasoning","content":"..."} / {"type":"delta","content":"你"}
 * / {"type":"done","messageId":12} / {"type":"error","content":"..."}（错误文案在 content，不是 message）。
 * <p>
 * Agent 事件：
 * {"type":"agent_status","content":"正在分析…"}
 * {"type":"tool_call","callId":"c1","tool":"search_blog","label":"搜索站内博客","args":{...}}
 * {"type":"tool_result","callId":"c1","status":"OK","summary":"找到 3 篇","durationMs":120}
 * <p>
 * reasoning 是推理模型的思考增量，只给前端展示、不入库；前端不认得这个 type 时忽略即可。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEvent(
        String type,
        String content,
        Long messageId,
        String model,
        @com.fasterxml.jackson.annotation.JsonProperty("prompt_tokens") Integer promptTokens,
        @com.fasterxml.jackson.annotation.JsonProperty("completion_tokens") Integer completionTokens,
        @com.fasterxml.jackson.annotation.JsonProperty("reasoning_tokens") Integer reasoningTokens,
        String callId,
        String tool,
        String label,
        Map<String, Object> args,
        String status,
        String summary,
        Long durationMs) {

    /** 思考过程增量，用于「思考中…」展示，不入库。 */
    public static ChatEvent reasoning(String content) {
        return new ChatEvent("reasoning", content, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static ChatEvent delta(String content) {
        return new ChatEvent("delta", content, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /**
     * 生成结束。除了 messageId，把这条回答的模型和用量一起带上：
     * 前端流式期间只有本地占位消息，不回填的话用量行要等刷新页面才出现。
     * 字段都是 NON_NULL，老前端只读 messageId 不受影响。
     */
    public static ChatEvent done(com.chatbot.chatbot.entity.Message saved) {
        return new ChatEvent("done", null, saved.getId(), saved.getModel(),
                saved.getPromptTokens(), saved.getCompletionTokens(), saved.getReasoningTokens(),
                null, null, null, null, null, null, null);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", message, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /** Agent 正在分析 / 整理，给前端一个可见的等待状态。 */
    public static ChatEvent agentStatus(String content) {
        return new ChatEvent("agent_status", content, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    public static ChatEvent toolCall(String callId, String tool, String label, Map<String, Object> args) {
        return new ChatEvent("tool_call", null, null, null, null, null, null,
                callId, tool, label, args, null, null, null);
    }

    public static ChatEvent toolResult(String callId, String status, String summary, long durationMs) {
        return new ChatEvent("tool_result", null, null, null, null, null, null,
                callId, null, null, null, status, summary, durationMs);
    }
}
