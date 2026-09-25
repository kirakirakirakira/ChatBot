package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.entity.User;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户信息（**本人视角**：登录响应、/me、改密码、改资料都是它）。
 * 没有 password 字段：BCrypt 哈希也不能出网，拿到哈希就能离线慢慢爆破。
 * mustChangePassword 是给「刚认证的这个会话」的指令（管理员重置过密码、本人还没改），
 * 登录响应和 /me 都带，刷新页面后强制改密框还能再弹出来。
 * <p>
 * 2026-09-25 起带上 nickname / email / phone / status / lastLoginAt：「个人信息」页要一屏展示完
 * 自己的账号情况，不该为了几个字段再多开一个接口。roleLabel / statusLabel 仍然由后端给中文，
 * 前端不维护 0/1/2/3 到中文的映射（口径见 Roles.label / UserStatus.label）。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        Integer role,
        String roleLabel,
        Integer status,
        String statusLabel,
        String email,
        String phone,
        String systemPrompt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        Boolean mustChangePassword) {

    /**
     * systemPrompt 只在这里带出：调用方是登录、/me、改密码、改资料、改人设这些「本人」的响应。
     * 管理员视角的列表走 AdminUserVO，那边连 systemPrompt 字段都没有——
     * 「能列用户」不等于「能看别人的人设」。
     */
    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                Roles.label(user.getRole()), user.getStatus(), UserStatus.label(user.getStatus()),
                user.getEmail(), user.getPhone(), user.getSystemPrompt(), user.getLastLoginAt(),
                user.getCreatedAt(), Boolean.TRUE.equals(user.getMustChangePassword()));
    }
}
