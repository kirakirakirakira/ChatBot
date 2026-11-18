package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.MessagePageVO;
import com.chatbot.chatbot.dto.MessageVO;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageCitationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import com.chatbot.chatbot.tool.ToolCitation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 引用卡落库 + 随消息读回的真库测试。
 * 锁住两件事：同一目标去重；GET messages 的 MessageVO.citations 带上可点击 url。
 */
@SpringBootTest
@Transactional
class MessageCitationStoreTest {

    @Autowired
    private MessageCitationStore citationStore;

    @Autowired
    private MessageCitationRepository citationRepository;

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
    void savesDedupesAndReadsBackWithMessages() {
        User owner = userRepository.findAll().stream().findFirst().orElseThrow();
        CurrentUser view = new CurrentUser(owner.getId(), owner.getUsername(), owner.getRole(), null, true);
        Conversation conversation = conversation(owner, "引用落库测试");
        Message answer = message(conversation, Role.ASSISTANT, "回答");
        Message cited = message(conversation, Role.USER, "被引用的消息");

        List<ToolCitation> citations = List.of(
                new ToolCitation(cited.getId(), conversation.getId(), "历史会话", "user", "片段一", LocalDateTime.now()),
                // 重复目标：模型可能把同一个 search 调两次，必须去重
                new ToolCitation(cited.getId(), conversation.getId(), "历史会话", "user", "片段二", LocalDateTime.now()),
                // 会话级命中：citedMessageId 为 null
                new ToolCitation(null, conversation.getId(), "只命中标题的会话", null, "摘要", LocalDateTime.now())
        );

        int saved = citationStore.save(answer.getId(), conversation.getId(), citations);
        assertEquals(2, saved);

        MessagePageVO page = conversationService.messages(conversation.getId(), view, null, 10);
        MessageVO answerVo = page.items().stream()
                .filter(item -> item.id().equals(answer.getId()))
                .findFirst().orElseThrow();

        assertNotNull(answerVo.citations());
        assertEquals(2, answerVo.citations().size());
        assertTrue(answerVo.citations().stream().anyMatch(c ->
                ("/chat?c=" + conversation.getId() + "&m=" + cited.getId()).equals(c.url())));
        assertTrue(answerVo.citations().stream().anyMatch(c ->
                ("/chat?c=" + conversation.getId()).equals(c.url())));
        assertEquals("user", answerVo.citations().get(0).role());
    }

    @Test
    void deletingConversationRemovesCitations() {
        User owner = userRepository.findAll().stream().findFirst().orElseThrow();
        CurrentUser view = new CurrentUser(owner.getId(), owner.getUsername(), owner.getRole(), null, true);
        Conversation conversation = conversation(owner, "删会话清引用");
        Message answer = message(conversation, Role.ASSISTANT, "回答");
        citationStore.save(answer.getId(), conversation.getId(), List.of(
                new ToolCitation(null, conversation.getId(), "会话", null, "摘要", LocalDateTime.now())));

        conversationService.delete(conversation.getId(), view);

        // 删会话后实体还在持久化上下文里（标记为 removed），不清掉的话下一条查询会先 flush 它，
        // 结果撞上「Message 引用已删除的 Conversation」。测试里手动 clear，生产代码不需要。
        entityManager.clear();
        assertTrue(citationRepository.findByMessageIdInOrderByIdAsc(List.of(answer.getId())).isEmpty());
    }

    private Conversation conversation(User owner, String title) {
        Conversation conversation = new Conversation();
        conversation.setOwner(owner);
        conversation.setTitle(title);
        return conversationRepository.saveAndFlush(conversation);
    }

    private Message message(Conversation conversation, Role role, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        return messageRepository.saveAndFlush(message);
    }
}
