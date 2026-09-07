package com.chatbot.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE 推送给前端的事件。四种 type：
 * - reasoning : 一段思考过程增量，文本在 content（推理模型才有，出现在正式回答之前）
 * - delta     : 一段正式回答增量，文本在 content
 * - done      : 本轮回复结束，助手消息已入库，id 在 messageId
 * - error     : 出错，错误文案在 content（注意：不是 message 字段）
 * <p>
 * NON_NULL 让 null 字段不出现在 JSON 里，前端按 type 取对应字段即可：
 * {"type":"reasoning","content":"..."} / {"type":"delta","content":"你"}
 * / {"type":"done","messageId":12} / {"type":"error","content":"..."}
 * <p>
 * reasoning 只是过程展示，不属于助手消息正文，后端不入库。
 * 前端不认识这个 type 时忽略即可，delta / done / error 的老契约完全没变。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEvent(String type, String content, Long messageId) {

    /** 思考过程增量：给前端显示「思考中…」和可折叠的思考内容用，不入库。 */
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
