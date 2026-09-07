package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送消息的请求体。
 *
 * @param message        用户消息，不能为空。
 * @param enableThinking 本次请求的思考开关（可选）：
 *                       true / false 只对这一条消息生效，覆盖服务端 llm.enable-thinking 配置；
 *                       不传该字段（null）则沿用服务端配置。
 */
public record ChatRequest(
        @NotBlank(message = "message 不能为空") String message,
        Boolean enableThinking) {
}