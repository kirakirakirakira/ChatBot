package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 管理端操作审计。只记**成功的写操作**，且与业务操作在同一个事务里写：
 * 业务回滚时审计一起回滚，不留「操作失败了却有一条留痕」的假记录。
 * <p>
 * actor / target 都存 id + 名字快照、**不建外键**：删号是功能，管理员自己也可能被删，
 * 留痕不该反过来挡住删人（外键 RESTRICT 会让删管理员直接报错）；目标被删之后靠名字快照认人。
 * <p>
 * action 存字符串，取值见 {@link AuditAction}（不用 ENUM 的理由在那边）。
 */
@Entity
@Table(name = "admin_audit_log",
        indexes = @Index(name = "idx_audit_created", columnList = "created_at"))
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作者 id。不建外键，理由见类注释。 */
    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    /** 操作者用户名的快照：账号被删之后这一列是唯一的线索。 */
    @Column(name = "actor_name", nullable = false, length = 50)
    private String actorName;

    /** 动作名，取值见 {@link AuditAction}。 */
    @Column(nullable = false, length = 32)
    private String action;

    /** 目标用户 id。DELETE_USER 之后目标行已不存在，这里留 id 做线索。 */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 目标用户名快照，理由同 actorName。 */
    @Column(name = "target_name", nullable = false, length = 50)
    private String targetName;

    /** 变更明细，如 "role 0 → 1"、"生成随机密码，强制下次改密"。给人看的，不做解析。 */
    @Column(length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
