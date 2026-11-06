package com.chatbot.chatbot.service;

import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 摘要落库走的是 @Modifying JPQL，必须真有事务；这个测试把「事务边界 + 查询字段名」一起锁住。
 * 用 @Transactional 回滚，不污染本地库。
 */
@SpringBootTest
@Transactional
class ConversationSummaryStoreTest {

    @Autowired
    private ConversationSummaryStore summaryStore;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesSummaryAndCoverageCursor() {
        User owner = userRepository.findAll().stream().findFirst().orElseThrow();
        Conversation conversation = new Conversation();
        conversation.setTitle("摘要测试");
        conversation.setOwner(owner);
        conversationRepository.saveAndFlush(conversation);

        summaryStore.save(conversation.getId(), "测试摘要", 0L);
        entityManager.clear();

        Conversation reloaded = conversationRepository.findById(conversation.getId()).orElseThrow();
        assertEquals("测试摘要", reloaded.getSummary());
        assertEquals(0L, reloaded.getSummaryUpToMessageId());
    }
}
