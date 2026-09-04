package com.chatbot.chatbot.llm;

/**
 * 传给 LLM 的一条消息（OpenAI 格式）。
 */
public record LlmMessage(String role, String content) {
}
