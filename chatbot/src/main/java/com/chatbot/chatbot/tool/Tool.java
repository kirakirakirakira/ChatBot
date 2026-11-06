package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.llm.LlmTool;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 一个可被模型调用的工具。实现类只做业务动作，不负责 SSE / 事务 / 超时：
 * 这些由 AgentService 统一处理。
 */
public interface Tool {

    String name();

    /** 中文展示名，用于 SSE 时间线。 */
    String label();

    /** 给模型看的用途说明：什么时候用、什么时候不用。 */
    String description();

    /** JSON Schema；模型据此生成 arguments。 */
    Map<String, Object> parameterSchema();

    ToolResult execute(ToolContext context, JsonNode arguments);

    default LlmTool definition() {
        return new LlmTool(name(), description(), parameterSchema());
    }
}
