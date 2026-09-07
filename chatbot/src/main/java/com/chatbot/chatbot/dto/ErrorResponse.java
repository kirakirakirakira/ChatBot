package com.chatbot.chatbot.dto;

import java.time.LocalDateTime;

/**
 * 统一错误响应体（非 SSE 接口）。
 * 字段刻意与 Spring 默认 /error 输出保持一致，前端只需要认这一种结构：
 * 读 status 做分支，读 message 直接展示。
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