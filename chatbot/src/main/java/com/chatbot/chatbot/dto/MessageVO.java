package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.Message;

import java.time.LocalDateTime;

public record MessageVO(Long id, String role, String content, LocalDateTime createdAt) {

    public static MessageVO from(Message m) {
        return new MessageVO(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getCreatedAt());
    }
}
