package com.chatbot.chatbot.dto;

import java.time.LocalDateTime;

/**
 * 统一错误响应体（非 SSE 接口）。字段与 Spring 默认 /error 输出一致：前端认 status 分支、读 message 展示。
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path);
    }
}