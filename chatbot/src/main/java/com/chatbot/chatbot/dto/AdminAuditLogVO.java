package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.AdminAuditLog;
import com.chatbot.chatbot.entity.AuditAction;

import java.time.LocalDateTime;

/**
 * 审计行出网形状。actionLabel 由后端给中文（和 roleLabel / statusLabel 同一个口径），
 * 前端不维护动作名映射；不认识的老动作名原样回传（见 AuditAction.label）。
 */
public record AdminAuditLogVO(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String actionLabel,
        Long targetId,
        String targetName,
        String detail,
        LocalDateTime createdAt) {

    public static AdminAuditLogVO from(AdminAuditLog log) {
        return new AdminAuditLogVO(log.getId(), log.getActorId(), log.getActorName(), log.getAction(),
                AuditAction.label(log.getAction()), log.getTargetId(), log.getTargetName(),
                log.getDetail(), log.getCreatedAt());
    }
}
