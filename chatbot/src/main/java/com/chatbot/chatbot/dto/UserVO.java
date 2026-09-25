package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.entity.User;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户信息。
 * 没有 password 字段：BCrypt 哈希也不能出网，拿到哈希就能离线慢慢爆破。
 * mustChangePassword 是给「刚认证的这个会话」的指令（管理员重置过密码、本人还没改），
 * 登录响应和 /me 都带，刷新页面后强制改密框还能再弹出来。
 */
public record UserVO(
        Long id,
        String username,
        Integer role,
        String roleLabel,
        String systemPrompt,
        LocalDateTime createdAt,
        Boolean mustChangePassword) {

    public static UserVO from(User user) {
        return from(user, true);
    }

    /**
     * @param includeSystemPrompt 管理员看用户列表时传 false：别人的人设属于个人设置，
     *                            不该因为「管理员能列用户」就顺带全看见。自己的 /me 和登录响应才带。
     */
    public static UserVO from(User user, boolean includeSystemPrompt) {
        return new UserVO(user.getId(), user.getUsername(), user.getRole(),
                Roles.label(user.getRole()), includeSystemPrompt ? user.getSystemPrompt() : null, user.getCreatedAt(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }
}