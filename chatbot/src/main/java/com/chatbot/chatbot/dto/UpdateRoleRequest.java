package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 改角色。合法值集合见 Roles，未知值在 AdminUserService 里判 400（不用注解的理由同 CreateUserRequest）。
 */
public record UpdateRoleRequest(@NotNull(message = "角色不能为空") Integer role) {
}
