package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.Conversation;

import java.time.LocalDateTime;

public record ConversationVO(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ConversationVO from(Conversation c) {
        return new ConversationVO(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
