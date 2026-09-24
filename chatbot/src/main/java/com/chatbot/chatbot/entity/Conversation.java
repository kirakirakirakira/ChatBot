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

import java.time.LocalDateTime;

/**
 * 会话（对应左侧会话列表的一项）。
 * <p>
 * owner 刻意是 {@code optional = false}：一个会话必须属于某个用户，不存在「公共会话」这种中间态。
 * 取单个会话一律走 {@code ConversationService.requireOwned(id, user)}，它按 (id, owner_id) 查；
 * <b>不要直接用 findById</b>，那会绕过归属校验，等于把别人的会话交出去。
 */
@Entity
@Table(name = "conversation",
        indexes = @Index(name = "idx_conversation_owner_updated", columnList = "owner_id, updated_at"))
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 归属用户。会话列表和单会话查询都必须带上它，这是「多用户 = 数据隔离」的落点。
     * LAZY：ConversationVO 不含用户信息，列表查询不需要把 sys_user 一起 join 出来。
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversation_owner"))
    private User owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 任何 update 都刷新 updatedAt：会话列表按它倒序排，不能只靠 ChatService 手动 set。 */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
