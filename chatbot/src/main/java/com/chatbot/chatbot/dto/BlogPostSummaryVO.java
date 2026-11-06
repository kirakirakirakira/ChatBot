package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * 博客列表的一行：不含 content。
 * status 用字符串出网（PostStatus.name()），statusLabel 由后端给中文——前端不维护枚举映射。
 */
public record BlogPostSummaryVO(
        Long id,
        String title,
        String summary,
        Long authorId,
        String authorName,
        String status,
        String statusLabel,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static BlogPostSummaryVO from(BlogPostSummaryView view) {
        PostStatus status = view.getStatus();
        return new BlogPostSummaryVO(view.getId(), view.getTitle(), view.getSummary(),
                view.getAuthorId(), view.getAuthorName(),
                status == null ? null : status.name(), status == null ? "未知状态" : status.label(),
                view.getPublishedAt(), view.getCreatedAt(), view.getUpdatedAt());
    }
}