package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户的长期记忆：跨会话生效、用户可见可编辑。
 * <p>
 * 和 system_prompt 刻意分开：system_prompt 是用户亲手写的人设，自动抽取的事实不能往里塞，
 * 否则用户下次打开设置会看到一段自己没写过的提示词。
 * <p>
 * source_conversation_id / source_message_id 只做溯源，**不加外键**：
 * 会话或消息被删时，记忆本身仍然应该保留；用户要删记忆有单独的入口。
 */
@Entity
@Table(name = "user_memory",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_memory_hash", columnNames = {"user_id", "content_hash"}),
        indexes = @Index(name = "idx_user_memory_user_status_updated", columnList = "user_id, status, updated_at"))
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_memory_user"))
    private User user;

    /** 取值见 MemoryKind；存字符串而不是 ENUM，新增类型不需要改列。 */
    @Column(nullable = false, length = 16)
    private String kind;

    /** 一句话事实，最长 500 字；入库前由 MemoryText 统一空白和截断。 */
    @Column(nullable = false, length = 500)
    private String content;

    /** 规范化内容的 SHA-256，用来在同一用户下做精确去重。 */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /** 0=自动，1=手动，取值见 MemorySource。 */
    @Column(nullable = false)
    private Integer source = MemorySource.AUTO.code();

    /** 0.00 ~ 1.00。自动抽取低于阈值不入库；手动维护固定 1.00。 */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence = BigDecimal.ONE;

    /** 0=启用，1=已归档，取值见 MemoryStatus。 */
    @Column(nullable = false)
    private Integer status = MemoryStatus.ACTIVE.code();

    /** 来源会话 / 消息，仅用于解释这条记忆从哪来；不做外键，删会话不连坐。 */
    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    /** 被注入 Prompt 的次数，用于以后做衰减 / 清理。 */
    @Column(name = "use_count", nullable = false)
    private Integer useCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Integer getSource() {
        return source;
    }

    public void setSource(Integer source) {
        this.source = source;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public Long getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(Long sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    public Integer getUseCount() {
        return useCount;
    }

    public void setUseCount(Integer useCount) {
        this.useCount = useCount;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
