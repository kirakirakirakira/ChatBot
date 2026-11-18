package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.MessagePageVO;
import com.chatbot.chatbot.dto.MessageToolCallVO;
import com.chatbot.chatbot.dto.MessageVO;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import com.chatbot.chatbot.tool.ToolResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 查询时间线回放的真库测试：工具调用先写审计表，助手消息入库后回填 message_id，
 * GET messages 再把精简步骤（工具 / 参数 / 状态 / 耗时）带回来。
 */
@SpringBootTest
@Transactional
class MessageToolCallReplayTest {

    @Autowired
    private AgentToolCallStore toolCallStore;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void attachesToolCallsAndReadsBackWithMessages() {
        User owner = userRepository.findAll().stream().findFirst().orElseThrow();
        CurrentUser view = new CurrentUser(owner.getId(), owner.getUsername(), owner.getRole(), null, true);
        Conversation conversation = new Conversation();
        conversation.setOwner(owner);
        conversation.setTitle("时间线回放测试");
        conversationRepository.saveAndFlush(conversation);

        Message answer = new Message();
        answer.setConversation(conversation);
        answer.setRole(Role.ASSISTANT);
        answer.setContent("回答");
        messageRepository.saveAndFlush(answer);

        String callId = "call_" + UUID.randomUUID();
        toolCallStore.record(conversation.getId(), owner.getId(), callId, "search_my_history", "搜索我的历史对话",
                "{\"query\":\"赤霄\",\"limit\":5}",
                ToolResult.ok("找到 2 条", Map.of("items", List.of())), 12);
        int attached = toolCallStore.attachToMessage(
                conversation.getId(), owner.getId(), List.of(callId), answer.getId());
        assertEquals(1, attached);

        entityManager.clear();
        MessagePageVO page = conversationService.messages(conversation.getId(), view, null, 10);
        MessageVO answerVo = page.items().stream()
                .filter(item -> item.id().equals(answer.getId()))
                .findFirst().orElseThrow();

        assertNotNull(answerVo.toolCalls());
        assertEquals(1, answerVo.toolCalls().size());
        MessageToolCallVO step = answerVo.toolCalls().get(0);
        assertEquals("search_my_history", step.tool());
        assertEquals("搜索我的历史对话", step.label());
        assertEquals("OK", step.status());
        assertEquals("赤霄", step.args().get("query"));
        assertEquals(12, step.durationMs());
    }
}
