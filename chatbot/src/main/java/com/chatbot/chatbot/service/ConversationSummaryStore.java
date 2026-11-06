package com.chatbot.chatbot.service;

import com.chatbot.chatbot.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 摘要落库的短事务边界。
 * <p>
 * 单独成 Bean 而不是在 {@link ConversationSummaryService} 里加 @Transactional：
 * 那边要等 LLM 返回，绝不能把模型调用包进数据库事务；这里只做一条 UPDATE。
 */
@Service
public class ConversationSummaryStore {

    private final ConversationRepository conversationRepository;

    public ConversationSummaryStore(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public void save(Long conversationId, String summary, Long upToMessageId) {
        conversationRepository.updateSummary(conversationId, summary, upToMessageId, LocalDateTime.now());
    }
}
