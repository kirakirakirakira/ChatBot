package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 历史检索的真库测试：锁住三件事——只搜自己的、默认排除当前会话、会话级命中能带出来。
 * 依赖 MySQL 的 ngram 全文索引；建不出来（非 MySQL / 没装插件）时跳过，不让 CI 假红。
 */
@SpringBootTest
@Transactional
class HistorySearchServiceTest {

    private static final String KEYWORD = "青霜测试词";

    @Autowired
    private HistorySearchService historySearchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void requireFullTextIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'message' "
                        + "and index_name = 'ft_message_content'", Integer.class);
        assumeTrue(count != null && count > 0, "需要 MySQL ngram 全文索引");
    }

    @Test
    void findsOwnHistoryAndExcludesCurrentConversationAndOtherUsers() {
        List<User> users = userRepository.findAll();
        assumeTrue(users.size() >= 2, "至少需要两个种子账号");
        User owner = users.get(0);
        User other = users.get(1);

        Conversation target = conversation(owner, "青霜测试词讨论");
        conversationMessage(target, Role.USER, "我们上次讨论过" + KEYWORD + "和断线重连");
        conversationMessage(target, Role.ASSISTANT, "是的，" + KEYWORD + "那部分已经解决了");

        Conversation current = conversation(owner, "当前会话");
        conversationMessage(current, Role.USER, "当前会话里也提到" + KEYWORD + "，但不应出现在结果里");

        Conversation foreign = conversation(other, KEYWORD + "的会话");
        conversationMessage(foreign, Role.USER, "别人的" + KEYWORD);

        HistorySearchService.SearchResult result = historySearchService.search(
                owner.getId(), current.getId(), KEYWORD, 10);

        assertTrue(result.messages().stream().anyMatch(h -> target.getId().equals(h.conversationId())),
                "应该搜到自己的历史消息");
        assertTrue(result.messages().stream().noneMatch(h -> current.getId().equals(h.conversationId())),
                "当前会话应该被排除");
        assertTrue(result.messages().stream().noneMatch(h -> foreign.getId().equals(h.conversationId())),
                "不能搜到别人的消息");
        assertTrue(result.conversations().stream().anyMatch(h -> target.getId().equals(h.conversationId())),
                "标题命中应该产生会话级结果");
        assertTrue(result.messages().stream().allMatch(h -> h.snippet().contains(KEYWORD)));
    }

    @Test
    void readsWindowAroundMessageAndRejectsForeignConversation() {
        List<User> users = userRepository.findAll();
        assumeTrue(users.size() >= 2, "至少需要两个种子账号");
        User owner = users.get(0);
        User other = users.get(1);

        Conversation conversation = conversation(owner, "窗口测试");
        Message first = conversationMessage(conversation, Role.USER, "第一条");
        Message middle = conversationMessage(conversation, Role.ASSISTANT, "第二条");
        Message last = conversationMessage(conversation, Role.USER, "第三条");

        CurrentUser ownerView = new CurrentUser(owner.getId(), owner.getUsername(), owner.getRole(), null, true);
        HistorySearchService.ConversationWindow window = historySearchService.readConversation(
                conversation.getId(), ownerView, middle.getId(), 4);

        List<Long> ids = window.messages().stream().map(Message::getId).toList();
        assertTrue(ids.contains(first.getId()));
        assertTrue(ids.contains(middle.getId()));
        assertTrue(ids.contains(last.getId()));

        Conversation foreign = conversation(other, "别人的会话");
        conversationMessage(foreign, Role.USER, "别人的内容");
        assertThrows(ResponseStatusException.class, () -> historySearchService.readConversation(
                foreign.getId(), ownerView, null, 5));
    }

    private Conversation conversation(User owner, String title) {
        Conversation conversation = new Conversation();
        conversation.setOwner(owner);
        conversation.setTitle(title);
        return conversationRepository.saveAndFlush(conversation);
    }

    private Message conversationMessage(Conversation conversation, Role role, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        return messageRepository.saveAndFlush(message);
    }
}
