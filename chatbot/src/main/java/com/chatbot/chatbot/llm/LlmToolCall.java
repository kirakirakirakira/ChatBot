package com.chatbot.chatbot.llm;

/**
 * 工具调用。序列化形状必须遵循 OpenAI 兼容协议：
 * {"id":"call_1","type":"function","function":{"name":"search_blog","arguments":"{\"query\":\"SSE\"}"}}
 * <p>
 * 不能把 name / argumentsJson 直接摊平在顶层：百炼会以“function 字段缺失”拒绝下一轮请求。
 */
public record LlmToolCall(String id, String type, Function function) {

    /** 解析响应 / 创建调用时的便捷构造。 */
    public LlmToolCall(String id, String name, String argumentsJson) {
        this(id, "function", new Function(name, argumentsJson));
    }

    public String name() {
        return function == null ? null : function.name();
    }

    public String argumentsJson() {
        return function == null ? null : function.arguments();
    }

    public record Function(String name, String arguments) {
    }
}
