package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 改账号状态（启用 / 禁用）。合法值集合见 UserStatus，未知值在 AdminUserService 里判 400。
 * 禁用后目标用户的下一个请求就被 AuthInterceptor 拦成 401，不用等 token 自然过期。
 */
public record UpdateStatusRequest(@NotNull(message = "状态不能为空") Integer status) {
}
