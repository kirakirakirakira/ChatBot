package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.MessageCitation;

import java.time.LocalDateTime;

/**
 * 一条回答上的引用来源（GET /conversations/{id}/messages 里随消息返回）。
 * <p>
 * 字段名刻意和 SSE tool_result.ui.citations 保持一致：前端可以用同一个 Citation 类型渲染，
 * 不需要区分「流式期间」和「刷新后」两套结构。
 * url 不落库，读的时候按 cited_* 现拼——域名 / 路由变了不用迁移数据。
 */
public record MessageCitationVO(
        Long messageId,
        Long conversationId,
        String conversationTitle,
        String role,
        String snippet,
        LocalDateTime createdAt,
        String url) {

    public static MessageCitationVO from(MessageCitation citation) {
        String url = citation.getCitedMessageId() == null
                ? "/chat?c=" + citation.getCitedConversationId()
                : "/chat?c=" + citation.getCitedConversationId() + "&m=" + citation.getCitedMessageId();
        return new MessageCitationVO(
                citation.getCitedMessageId(),
                citation.getCitedConversationId(),
                citation.getConversationTitle(),
                citation.getRole(),
                citation.getSnippet(),
                citation.getQuotedAt(),
                url);
    }
}
