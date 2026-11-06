package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.MemoryKind;
import com.chatbot.chatbot.entity.MemorySource;
import com.chatbot.chatbot.entity.UserMemory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 记忆管理页的一行。kindLabel / sourceLabel 由后端给中文，前端不维护枚举映射。 */
public record UserMemoryVO(
        Long id,
        String kind,
        String kindLabel,
        String content,
        String source,
        String sourceLabel,
        BigDecimal confidence,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastUsedAt) {

    public static UserMemoryVO from(UserMemory memory) {
        return new UserMemoryVO(
                memory.getId(),
                memory.getKind(),
                MemoryKind.labelOf(memory.getKind()),
                memory.getContent(),
                memory.getSource() != null && memory.getSource() == MemorySource.MANUAL.code() ? "MANUAL" : "AUTO",
                MemorySource.labelOf(memory.getSource()),
                memory.getConfidence(),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getLastUsedAt());
    }
}
