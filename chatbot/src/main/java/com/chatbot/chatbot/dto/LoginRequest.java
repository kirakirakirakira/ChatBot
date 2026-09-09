package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体。刻意不加 @Size / @Pattern：格式校验只会把「密码错了」变成「格式不合法」，白给爆破的人送信息。
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}