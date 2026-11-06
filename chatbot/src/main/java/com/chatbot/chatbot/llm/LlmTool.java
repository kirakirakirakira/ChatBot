package com.chatbot.chatbot.llm;

import java.util.Map;

/**
 * 暴露给模型的工具定义。parameters 必须是 JSON Schema（type / properties / required），
 * 模型只能看到这份描述，看不到工具实现。
 */
public record LlmTool(String name, String description, Map<String, Object> parameters) {
}
