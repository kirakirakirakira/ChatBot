package com.chatbot.chatbot.dto;

/**
 * SSE 推送给前端的事件。
 * - delta: 一段增量文本
 * - done : 本轮回复结束，messageId 为已入库的助手消息 id
 * - error: 出错
 */
public record ChatEvent(String type, String content, Long messageId) {

    public static ChatEvent delta(String content) {
        return new ChatEvent("delta", content, null);
    }

    public static ChatEvent done(Long messageId) {
        return new ChatEvent("done", null, messageId);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", message, null);
    }
}
