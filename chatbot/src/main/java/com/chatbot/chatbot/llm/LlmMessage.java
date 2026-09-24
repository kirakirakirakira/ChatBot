package com.chatbot.chatbot.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 传给 LLM 的一条消息（OpenAI 格式）。
 *
 * @param reasoningContent 助手消息的历史思考过程。qwen3.8-max / qwen3.8-flash 的 preserve_thinking 默认 true，
 *                         要求把历史 reasoning_content 完整回传（官方 Chat 文档）；没有就为 null、不下发该字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmMessage(
        String role,
        String content,
        @JsonProperty("reasoning_content") String reasoningContent) {

    /** 不带思考的普通消息。 */
    public static LlmMessage of(String role, String content) {
        return new LlmMessage(role, content, null);
    }
}
