package com.chatbot.chatbot.service;

import com.chatbot.chatbot.config.MemoryProperties;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.llm.LlmCallOptions;
import com.chatbot.chatbot.llm.LlmMessage;
import com.chatbot.chatbot.llm.LlmProperties;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话摘要：把超出上下文窗口的旧消息压成一段事实性摘要。
 * <p>
 * 触发时机是每轮回答结束后的异步任务，不阻塞 SSE。摘要是派生数据，失败可以重试；
 * 即使一直失败，也只是退回「只看最近历史」的旧行为，不会破坏聊天。
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    /** 单实例内的并发闸门：同一会话同一时间只跑一个摘要任务。多实例部署时需换成数据库行锁。 */
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final LlmProperties llmProperties;
    private final MemoryProperties memoryProperties;
    private final LlmTextService llmTextService;
    private final ConversationSummaryStore summaryStore;

    public ConversationSummaryService(ConversationRepository conversationRepository,
                                      MessageRepository messageRepository,
                                      LlmProperties llmProperties,
                                      MemoryProperties memoryProperties,
                                      LlmTextService llmTextService,
                                      ConversationSummaryStore summaryStore) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.llmProperties = llmProperties;
        this.memoryProperties = memoryProperties;
        this.llmTextService = llmTextService;
        this.summaryStore = summaryStore;
    }

    public void maybeSummarize(Long conversationId) {
        if (!memoryProperties.enabled() || !running.add(conversationId)) {
            return;
        }
        try {
            Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
            if (conversation == null) {
                return;
            }
            Long afterId = conversation.getSummaryUpToMessageId() == null
                    ? 0L : conversation.getSummaryUpToMessageId();

            long pendingCount = messageRepository.countByConversationIdAndIdGreaterThan(conversationId, afterId);
            if (pendingCount == 0) {
                return;
            }
            int triggerMessages = memoryProperties.summaryTriggerMessages();
            if (triggerMessages <= 0 || pendingCount < triggerMessages) {
                List<Message> pending = messageRepository
                        .findByConversationIdAndIdGreaterThanOrderByIdAsc(
                                conversationId, afterId, PageRequest.of(0, 200));
                int tokens = pending.stream()
                        .mapToInt(m -> MemoryText.estimateTokens(m.getContent())
                                + MemoryText.estimateTokens(m.getReasoning()))
                        .sum();
                if (tokens < memoryProperties.summaryTriggerTokens()) {
                    return;
                }
            }

            List<Message> batch = messageRepository
                    .findByConversationIdAndIdGreaterThanOrderByIdAsc(
                            conversationId, afterId,
                            PageRequest.of(0, Math.max(1, memoryProperties.summaryBatchMessages())));
            if (batch.isEmpty()) {
                return;
            }

            String model = UtilityModel.resolve(llmProperties, memoryProperties.summaryModel());
            if (model == null) {
                return;
            }

            String prompt = buildPrompt(conversation.getSummary(), batch);
            String summary = llmTextService.complete(
                    List.of(
                            LlmMessage.of("system",
                                    "你是会话摘要器。只保留事实、决定、约束、专有名词、数字和未解决问题；"
                                            + "不要编造，不要保留寒暄和模型思考过程。"),
                            LlmMessage.of("user", prompt)),
                    new LlmCallOptions(model, false, null, false));
            summary = trim(summary, Math.max(200, memoryProperties.summaryMaxChars()));
            if (summary.isBlank()) {
                return;
            }

            Long upTo = batch.get(batch.size() - 1).getId();
            summaryStore.save(conversationId, summary, upTo);
            log.debug("会话摘要已更新 conversationId={} upTo={} chars={}", conversationId, upTo, summary.length());
        } catch (Exception e) {
            log.warn("会话摘要失败 conversationId={}: {}", conversationId, describe(e));
        } finally {
            running.remove(conversationId);
        }
    }

    private String buildPrompt(String existingSummary, List<Message> batch) {
        StringBuilder sb = new StringBuilder();
        sb.append("已有摘要：\n")
                .append(existingSummary == null || existingSummary.isBlank() ? "（无）" : existingSummary)
                .append("\n\n新增对话：\n");
        for (Message message : batch) {
            String content = message.getContent() == null ? "" : message.getContent();
            if (content.length() > 4000) {
                content = content.substring(0, 4000) + "…";
            }
            sb.append('[').append(message.getRole().name()).append("] ").append(content).append('\n');
        }
        sb.append("\n把两部分合并成一份新的会话摘要，不超过 ")
                .append(Math.max(200, memoryProperties.summaryMaxChars())).append(" 字。只输出摘要正文。");
        return sb.toString();
    }

    private static String trim(String raw, int maxChars) {
        String text = raw == null ? "" : raw.strip();
        if (text.length() > maxChars) {
            return text.substring(0, maxChars).strip();
        }
        return text;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }
}
