package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.entity.BlogPost;
import com.chatbot.chatbot.entity.PostStatus;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.BlogPostRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SearchBlogToolTest {

    @Autowired
    private SearchBlogTool tool;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchesPublishedPostsByTitleOrSummary() throws Exception {
        User author = userRepository.findAll().stream().findFirst().orElseThrow();
        BlogPost post = new BlogPost();
        post.setAuthor(author);
        post.setTitle("SSE 实战测试");
        post.setSummary("SseEmitter 断线重连");
        post.setContent("# 正文");
        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());
        blogPostRepository.saveAndFlush(post);

        ToolResult result = tool.execute(
                new ToolContext(new CurrentUser(author.getId(), author.getUsername(), author.getRole(), null, true), 1L, "call_search"),
                objectMapper.readTree("{\"query\":\"SSE\",\"limit\":5}"));

        assertTrue(result.ok());
        List<?> items = (List<?>) result.data().get("items");
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(item -> String.valueOf(item).contains("SSE 实战测试")));
    }
}
