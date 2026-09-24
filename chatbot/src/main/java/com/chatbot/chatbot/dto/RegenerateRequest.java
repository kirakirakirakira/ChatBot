package com.chatbot.chatbot.dto;

/**
 * 重新生成的请求体。整个体都可以省略（{@code @RequestBody(required = false)}）。
 *
 * @param enableThinking 本次重跑的思考开关，语义与 {@link ChatRequest#enableThinking()} 完全一致。
 */
public record RegenerateRequest(
        Boolean enableThinking,
        /** 本次重跑使用的模型 id；null = 沿用被删掉那条回答的模型，再退回服务端默认。 */
        String model,
        Integer thinkingBudget) {
}
