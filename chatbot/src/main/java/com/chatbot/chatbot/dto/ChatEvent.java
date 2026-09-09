package com.chatbot.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE 推送给前端的事件。NON_NULL 让 null 字段不出现在 JSON 里，前端按 type 取对应字段：
 * {"type":"reasoning","content":"..."} / {"type":"delta","content":"你"}
 * / {"type":"done","messageId":12} / {"type":"error","content":"..."}（错误文案在 content，不是 message）。
 * <p>
 * reasoning 是推理模型的思考增量，只给前端展示、不入库；前端不认得这个 type 时忽略即可。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEvent(String type, String content, Long messageId) {

    /** 思考过程增量，用于「思考中…」展示，不入库。 */
    public static ChatEvent reasoning(String content) {
        return new ChatEvent("reasoning", content, null);
    }

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
