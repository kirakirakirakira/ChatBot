package com.chatbot.chatbot.service;

import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.AgentToolCallRepository;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.UserRepository;
import com.chatbot.chatbot.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class AgentToolCallStoreTest {

    @Autowired
    private AgentToolCallStore store;

    @Autowired
    private AgentToolCallRepository repository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void recordsToolCallWithResultSummaryAndStatus() {
        User owner = userRepository.findAll().stream().findFirst().orElseThrow();
        Conversation conversation = new Conversation();
        conversation.setTitle("工具日志测试");
        conversation.setOwner(owner);
        conversationRepository.saveAndFlush(conversation);

        String callId = "call_" + UUID.randomUUID();
        store.record(conversation.getId(), owner.getId(), callId, "calculator",
                "{\"expression\":\"1+1\"}", ToolResult.ok("1+1 = 2", Map.of("result", "2")), 3);

        long count = repository.findAll().stream()
                .filter(row -> callId.equals(row.getCallId()))
                .count();
        assertEquals(1, count);
        assertEquals("OK", repository.findAll().stream()
                .filter(row -> callId.equals(row.getCallId()))
                .findFirst().orElseThrow().getStatus());
    }
}
