package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求体。
 * <p>
 * 这里刻意不加 @Size / @Pattern：登录是「拿用户输入去比对已有账号」，
 * 加格式校验只会把「密码错了」变成「格式不合法」，白白给爆破的人送信息。
 * 格式约束应该加在注册 / 改密码这类写入接口上。
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {
}