package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 博客文章。
 * <p>
 * 和会话不同，文章是**全站可读**的：列表查询不按 owner 过滤，只有编辑 / 删除才按作者校验。
 * 所以单篇查询不能只靠 {@code findById} 放行——详情接口自己判断「已发布 or 作者本人」，见 BlogService.detail。
 * <p>
 * status 列刻意声明成 varchar(20)：Hibernate 在 MySQL 上会把 {@code @Enumerated(STRING)} 默认建成 ENUM，
 * 而状态值以后要加（草稿 / 待审 / 驳回 / 下架），ENUM 每加一个值都要手工 ALTER。
 */
@Entity
@Table(name = "blog_post",
        indexes = {
                @Index(name = "idx_blog_status_published", columnList = "status, published_at"),
                @Index(name = "idx_blog_author_created", columnList = "author_id, created_at")
        })
public class BlogPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作者。删号时由 AdminUserService 先按 author_id 批量删文章，再删用户行（外键 RESTRICT）。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_blog_post_author"))
    private User author;

    @Column(nullable = false, length = 100)
    private String title;

    /**
     * 列表摘要，最长 200。单独存一列而不是列表时截取 content：
     * content 是 LONGTEXT，列表页只需要摘要，没必要每页都把几十 KB 正文拉进内存。
     * 作者没填时由 BlogService 从正文生成；正文只有代码块等提不出文字时为 NULL。
     */
    @Column(length = 200)
    private String summary;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private PostStatus status;

    /** 发布时间；草稿 / 待审 / 驳回为 NULL。列表按它倒序，编辑不改变它，避免老文章被顶到最前面。 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 任何内容 / 状态变更都刷新 updatedAt；排序永远用 publishedAt，不用这一列。 */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}