package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审计只读不改：没有 update / delete 方法是有意的——审计表一旦能改就失去了「留痕」的意义。
 * 真要清理历史（合规要求保留期之类），走运维 SQL 并留下工单，不给应用开这个口。
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
