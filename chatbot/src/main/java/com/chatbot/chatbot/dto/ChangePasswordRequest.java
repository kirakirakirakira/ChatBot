package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改自己的密码。
 * <p>
 * oldPassword 只有 @NotBlank、没有长度限制：初始管理员的密码就是 5 位的 admin，
 * 给原密码套 @Size(min = 6) 会导致 admin 永远改不了自己的密码。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需在 6~64 之间") String newPassword) {
}