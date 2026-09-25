package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.entity.User;

import java.time.LocalDateTime;

/**
 * 管理端看到的用户。字段顺序即界面列顺序。
 * <p>
 * 刻意不复用 {@link UserVO}：UserVO 带 systemPrompt（别人的人设不该因为「管理员能列用户」就全看见），
 * 而管理列表要 status / statusLabel / lastLoginAt 这些 UserVO 里没有的列。
 * 两个 VO 各服务一屏，加字段时互不牵扯。
 * <p>
 * 不含 password 和 systemPrompt：哈希出网等于递给人一份可以离线慢慢爆破的素材。
 * roleLabel / statusLabel 由后端给出，前端不维护 0/1 到中文的映射（口径见 Roles.label / UserStatus.label）。
 * <p>
 * nickname / email 带上是给管理员「认人」用的：批量操作选错一个 id 的代价很高，
 * 表格里只有登录名时，两个相似的账号很容易点错行。手机号刻意不带——管理列表用不上，少一列个人信息出网。
 */
public record AdminUserVO(
        Long id,
        String username,
        String nickname,
        Integer role,
        String roleLabel,
        Integer status,
        String statusLabel,
        String email,
        LocalDateTime lastLoginAt,
        Boolean mustChangePassword,
        LocalDateTime createdAt,
        Boolean canManage) {

    /**
     * @param actorRank 当前操作者的管理层级（{@code CurrentUser.rank()}）。传 null 表示不算层级（不应发生）。
     * canManage = 目标层级严格低于操作者：前端据此**禁用**行内控件，而不是「看起来能点、点了才 400」。
     */
    public static AdminUserVO from(User user, Integer actorRank) {
        boolean canManage = actorRank != null && Roles.rank(user.getRole()) < actorRank;
        return new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                Roles.label(user.getRole()), user.getStatus(), UserStatus.label(user.getStatus()),
                user.getEmail(), user.getLastLoginAt(),
                Boolean.TRUE.equals(user.getMustChangePassword()), user.getCreatedAt(), canManage);
    }
}
