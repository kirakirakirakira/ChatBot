package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.Message;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param reasoning 思考过程全文；没开启思考或非推理模型时为 null。
 *                  NON_NULL 让它不出现在 JSON 里，老前端和瘦 payload 都受益。
 *                  model / 三个 token 计数同理：没有就不下发。
 * @param attachments 这条消息带的图片（只有用户消息会有）。没图时传 null 而不是空数组：
 *                    NON_NULL 会把它整个字段省掉，纯文本会话的响应体一点不变大。
 *                    只有元信息，字节要另外调 GET /api/attachments/{id}。
 * @param citations 这条回答展示过的引用卡（目前只有历史检索会产生）。没有时同样不下发；
 *                  有值时前端在刷新 / 切会话后仍能渲染，不必依赖当轮 SSE 的 agentSteps。
 * @param toolCalls 这条回答用过的工具步骤（已挂到 message_id 的 agent_tool_call）。没有时不下发；
 *                  前端刷新后用它折叠回放「查询过程」。
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
        List<AttachmentVO> attachments,
        List<MessageCitationVO> citations,
        List<MessageToolCallVO> toolCalls,
        LocalDateTime createdAt) {

    public static MessageVO from(Message m, List<AttachmentVO> attachments,
                                 List<MessageCitationVO> citations, List<MessageToolCallVO> toolCalls) {
        return new MessageVO(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getReasoning(),
                m.getModel(), m.getPromptTokens(), m.getCompletionTokens(), m.getReasoningTokens(),
                (attachments == null || attachments.isEmpty()) ? null : attachments,
                (citations == null || citations.isEmpty()) ? null : citations,
                (toolCalls == null || toolCalls.isEmpty()) ? null : toolCalls,
                m.getCreatedAt());
    }
}
