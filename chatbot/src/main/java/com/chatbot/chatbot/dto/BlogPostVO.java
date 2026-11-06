package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.BlogPost;
import com.chatbot.chatbot.entity.PostStatus;
import com.chatbot.chatbot.entity.User;

import java.time.LocalDateTime;

/**
 * 博客详情：带正文，只在「看单篇」时返回。
 * <p>
 * 作者名取昵称优先、回退用户名（和界面上的 displayName 同一口径）。调用方必须处在事务里：
 * author 是 LAZY，nickname / username 需要 entityManager 还开着。
 */
public record BlogPostVO(
        Long id,
        String title,
        String summary,
        String content,
        Long authorId,
        String authorName,
        String status,
        String statusLabel,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static BlogPostVO from(BlogPost post) {
        User author = post.getAuthor();
        String authorName = author.getNickname() == null || author.getNickname().isBlank()
                ? author.getUsername()
                : author.getNickname();
        PostStatus status = post.getStatus();
        return new BlogPostVO(post.getId(), post.getTitle(), post.getSummary(), post.getContent(),
                author.getId(), authorName,
                status == null ? null : status.name(), status == null ? "未知状态" : status.label(),
                post.getPublishedAt(), post.getCreatedAt(), post.getUpdatedAt());
    }
}