package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送消息的请求体。
 *
 * @param message        用户消息，不能为空。
 * @param enableThinking 本次请求的思考开关，true / false 只对本条消息生效并覆盖 llm.enable-thinking；为 null 时沿用服务端配置。
 */
public record ChatRequest(
        @NotBlank(message = "message 不能为空") String message,
        Boolean enableThinking) {
}