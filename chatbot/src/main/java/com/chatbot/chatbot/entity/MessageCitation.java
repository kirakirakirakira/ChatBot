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
 * 一条回答里展示的引用来源。
 * <p>
 * 为什么单独建表而不是塞进 agent_tool_call：工具调用是审计 / 排查数据，
 * 引用是「这条回答展示了什么」的产品数据。两者生命周期不同——工具日志删了不影响回答，
 * 引用必须跟着消息一起展示 / 一起删除。分开也避免为了回放 UI 去解析审计 JSON。
 * <p>
 * message_id 指向助手消息，是展示的归属；cited_* 指向被引用的历史消息。
 * 刻意不建外键：message 的级联删除由 ConversationService / AdminUserService 显式按顺序做，
 * 这张表只是展示数据，漏删最多是孤儿行，不会像 RESTRICT 那样把删会话卡死。
 */
@Entity
@Table(name = "message_citation",
        indexes = {
                @Index(name = "idx_message_citation_message", columnList = "message_id"),
                @Index(name = "idx_message_citation_conversation", columnList = "conversation_id")
        })
public class MessageCitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 这条引用属于哪条助手消息。 */
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** 助手消息所在的会话；删会话 / 删用户时按它批量清理。 */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 被引用的消息 id；null = 只有会话级命中（标题 / 摘要命中）。 */
    @Column(name = "cited_message_id")
    private Long citedMessageId;

    @Column(name = "cited_conversation_id", nullable = false)
    private Long citedConversationId;

    /** 标题快照：会话以后改名，旧回答仍显示当时的标题。 */
    @Column(name = "conversation_title", nullable = false, length = 120)
    private String conversationTitle;

    /** user / assistant；会话级命中为 null。 */
    @Column(length = 16)
    private String role;

    /** 展示用片段，已由工具截断（约 180 字）；这里再留一倍余量。 */
    @Column(length = 500)
    private String snippet;

    /** 被引用内容的时间；会话级命中是会话更新时间。 */
    @Column(name = "quoted_at")
    private LocalDateTime quotedAt;

    /** 来源工具；目前只有 HISTORY，留给以后博客 / 文件引用。 */
    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getCitedMessageId() {
        return citedMessageId;
    }

    public void setCitedMessageId(Long citedMessageId) {
        this.citedMessageId = citedMessageId;
    }

    public Long getCitedConversationId() {
        return citedConversationId;
    }

    public void setCitedConversationId(Long citedConversationId) {
        this.citedConversationId = citedConversationId;
    }

    public String getConversationTitle() {
        return conversationTitle;
    }

    public void setConversationTitle(String conversationTitle) {
        this.conversationTitle = conversationTitle;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public LocalDateTime getQuotedAt() {
        return quotedAt;
    }

    public void setQuotedAt(LocalDateTime quotedAt) {
        this.quotedAt = quotedAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
