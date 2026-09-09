package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改自己的密码。
 * oldPassword 只有 @NotBlank、不限长度：初始管理员密码就是 5 位，给它套 @Size(min = 6) 会让人永远改不了密码。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度需在 6~64 之间") String newPassword) {
}