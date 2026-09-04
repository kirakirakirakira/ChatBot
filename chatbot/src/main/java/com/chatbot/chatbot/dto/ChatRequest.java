package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送消息的请求体。
 */
public record ChatRequest(@NotBlank(message = "message 不能为空") String message) {
}
