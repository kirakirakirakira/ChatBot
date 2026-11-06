package com.chatbot.chatbot.llm;

import java.util.List;

/**
 * 非流式 planner 的一次输出：要么是文本，要么是一组工具调用。
 * 两者都可能有值，由 AgentService 决定下一步。
 */
public record LlmTurn(String content, List<LlmToolCall> toolCalls) {

    public LlmTurn {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
