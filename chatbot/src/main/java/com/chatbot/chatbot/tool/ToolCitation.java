package com.chatbot.chatbot.tool;

import java.time.LocalDateTime;

/**
 * 工具返回的一条引用来源（类型化版本）。
 * <p>
 * 为什么不全靠 {@link ToolResult#ui()}：SSE 的 ui 是前端契约，只能放 Map；
 * 落库如果也去解析 Map，字段一改就是运行时才炸。工具同时产出 typed 列表，
 * AgentService 直接收集、ChatService 直接落库，两边都不碰字符串键。
 *
 * @param citedMessageId      被引用的消息 id；null = 只有会话级命中
 * @param citedConversationId 被引用的会话 id
 * @param conversationTitle   被引用会话的标题快照（会话改名后，旧回答仍显示当时的标题）
 * @param role                被引用消息的角色：user / assistant；会话级命中为 null
 * @param snippet             展示用片段，已由工具截断
 * @param citedAt             被引用内容的时间；会话级命中是会话更新时间
 */
public record ToolCitation(Long citedMessageId,
                           Long citedConversationId,
                           String conversationTitle,
                           String role,
                           String snippet,
                           LocalDateTime citedAt) {
}
