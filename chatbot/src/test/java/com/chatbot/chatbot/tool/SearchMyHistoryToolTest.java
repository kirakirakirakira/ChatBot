package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import com.chatbot.chatbot.service.ConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 锁住工具返回的 ui.citations 形状：前端引用卡依赖 key / conversationId / url。
 * <p>
 * 刻意不加 @Transactional：InnoDB 的全文索引在提交后才对 MATCH 可见，
 * 同一个未提交事务里刚插入的消息搜不到，测试会变成「只测到 LIKE 兜底」。
 * 代价是要在 @AfterEach 里显式删掉造出来的会话，不能污染本地库。
 */
@SpringBootTest
class SearchMyHistoryToolTest {

    private static final String KEYWORD = "靛霜魍魉标记";

    @Autowired
    private SearchMyHistoryTool tool;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Conversation created;
    private CurrentUser ownerView;

    @BeforeEach
    void setUp() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.statistics "
                        + "where table_schema = database() and table_name = 'message' "
                        + "and index_name = 'ft_message_content'", Integer.class);
        assumeTrue(count != null && count > 0, "需要 MySQL ngram 全文索引");
    }

    @AfterEach
    void cleanUp() {
        if (created != null && ownerView != null) {
            conversationService.delete(created.getId(), ownerView);
            created = null;
        }
    }

    @Test
    void returnsClickableCitations() throws Exception {
        User owner = userRepository.findAll().stream().findFirst().orElseThrow();
        ownerView = new CurrentUser(owner.getId(), owner.getUsername(), owner.getRole(), null, true);

        created = new Conversation();
        created.setOwner(owner);
        created.setTitle("引用卡测试");
        conversationRepository.saveAndFlush(created);

        Message message = new Message();
        message.setConversation(created);
        message.setRole(Role.USER);
        message.setContent("这里提到一个独特词：" + KEYWORD + "，用来验证引用卡");
        messageRepository.saveAndFlush(message);

        ToolResult result = tool.execute(
                new ToolContext(ownerView, 999L, "call_history"),
                objectMapper.readTree("{\"query\":\"" + KEYWORD + "\",\"limit\":5}"));

        assertTrue(result.ok());
        // typed citations 是落库来源；role 必须已归一化成小写（前端按 user / assistant 判断）
        assertFalse(result.citations().isEmpty());
        assertEquals("user", result.citations().get(0).role());
        assertEquals(message.getId(), result.citations().get(0).citedMessageId());
        List<?> citations = (List<?>) result.ui().get("citations");
        assertFalse(citations.isEmpty());
        String first = String.valueOf(citations.get(0));
        assertTrue(first.contains("conversationId=" + created.getId()), first);
        assertTrue(first.contains("/chat?c=" + created.getId()), first);
        assertTrue(first.contains("m-" + message.getId()), first);
    }
}
