package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * 列表查询的投影：只取摘要需要的列，**不取 content**。
 * <p>
 * 用接口投影而不是「查实体再转 VO」：实体一加载就会把 LONGTEXT 正文一起拉出来，
 * 20 篇文章一页就是几百 KB 的无用 IO。JPQL 里的 CASE 负责「昵称优先、没昵称回退用户名」，
 * 避免逐条触发 LAZY 作者查询（open-in-view=false 下还会直接报错）。
 */
public interface BlogPostSummaryView {

    Long getId();

    String getTitle();

    String getSummary();

    Long getAuthorId();

    String getAuthorName();

    PostStatus getStatus();

    LocalDateTime getPublishedAt();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}