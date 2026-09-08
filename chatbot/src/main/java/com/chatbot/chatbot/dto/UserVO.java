package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.entity.User;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户信息。
 * <p>
 * 没有 password 字段：BCrypt 哈希也不能出网（拿到哈希就能离线慢慢爆破）。
 */
public record UserVO(
        Long id,
        String username,
        Integer role,
        String roleLabel,
        LocalDateTime createdAt) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRole(),
                Roles.label(user.getRole()), user.getCreatedAt());
    }
}