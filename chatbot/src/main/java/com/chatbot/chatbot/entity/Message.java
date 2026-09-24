package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 单条聊天消息。 */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /**
     * 思考过程全文（推理模型 + 开启思考时才有），可空。
     * 2026-09-24 之前 reasoning 只推前端不入库，刷新即丢；现在落库，刷新后仍能展开重读。
     * 空白时存 null 而不是空串：前端靠「有没有值」决定要不要渲染折叠块。
     */
    @Column(columnDefinition = "LONGTEXT")
    private String reasoning;

    /** 生成本条回答实际使用的模型 id；用户消息为 NULL。留着是为了事后能对账「哪条回答烧了哪个模型」。 */
    @Column(length = 64)
    private String model;

    /** 输入 token 数（含历史上下文）。流式用量来自最后一帧 usage；拿不到就保持 NULL，不填 0 冒充真实值。 */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    /** 输出 token 数（思考 + 正式回答）。 */
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /** 其中思考占的输出 token；服务商没给 details 时为 NULL。 */
    @Column(name = "reasoning_tokens")
    private Integer reasoningTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getReasoningTokens() {
        return reasoningTokens;
    }

    public void setReasoningTokens(Integer reasoningTokens) {
        this.reasoningTokens = reasoningTokens;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
