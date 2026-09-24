package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.Message;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * @param reasoning 思考过程全文；没开启思考或非推理模型时为 null。
 *                  NON_NULL 让它不出现在 JSON 里，老前端和瘦 payload 都受益。
 *                  model / 三个 token 计数同理：没有就不下发。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageVO(
        Long id,
        String role,
        String content,
        String reasoning,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer reasoningTokens,
        LocalDateTime createdAt) {

    public static MessageVO from(Message m) {
        return new MessageVO(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getReasoning(),
                m.getModel(), m.getPromptTokens(), m.getCompletionTokens(), m.getReasoningTokens(), m.getCreatedAt());
    }
}
