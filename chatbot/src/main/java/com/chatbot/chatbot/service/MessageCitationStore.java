package com.chatbot.chatbot.service;

import com.chatbot.chatbot.entity.MessageCitation;
import com.chatbot.chatbot.repository.MessageCitationRepository;
import com.chatbot.chatbot.tool.ToolCitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把工具产生的引用写到助手消息上。
 * <p>
 * 短事务、失败只记日志：引用是展示增强，不能因为它写失败就让用户已经看到的回答回滚。
 * 去重按 (citedConversationId, citedMessageId)：模型可能把同一个 search 调两次，
 * 而 mysql 的 unique key 对 NULL（会话级命中）不生效，所以去重放在 Java 里做。
 */
@Service
public class MessageCitationStore {

    private static final Logger log = LoggerFactory.getLogger(MessageCitationStore.class);
    private static final int MAX_PER_MESSAGE = 50;

    private final MessageCitationRepository repository;

    public MessageCitationStore(MessageCitationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int save(Long messageId, Long conversationId, List<ToolCitation> citations) {
        if (messageId == null || conversationId == null || citations == null || citations.isEmpty()) {
            return 0;
        }
        Map<String, ToolCitation> unique = new LinkedHashMap<>();
        for (ToolCitation citation : citations) {
            if (citation == null || citation.citedConversationId() == null) {
                continue;
            }
            String key = citation.citedConversationId() + ":"
                    + (citation.citedMessageId() == null ? "-" : citation.citedMessageId());
            unique.putIfAbsent(key, citation);
        }
        int saved = 0;
        for (ToolCitation citation : unique.values()) {
            if (saved >= MAX_PER_MESSAGE) {
                log.debug("引用超过单条消息上限，已截断 messageId={} total={}", messageId, unique.size());
                break;
            }
            repository.save(toEntity(messageId, conversationId, citation));
            saved++;
        }
        return saved;
    }

    private static MessageCitation toEntity(Long messageId, Long conversationId, ToolCitation citation) {
        MessageCitation row = new MessageCitation();
        row.setMessageId(messageId);
        row.setConversationId(conversationId);
        row.setCitedMessageId(citation.citedMessageId());
        row.setCitedConversationId(citation.citedConversationId());
        row.setConversationTitle(clip(citation.conversationTitle(), 120, "未命名会话"));
        row.setRole(citation.role() == null ? null : citation.role().toLowerCase());
        row.setSnippet(clip(citation.snippet(), 500, null));
        row.setQuotedAt(citation.citedAt());
        row.setSource("HISTORY");
        return row;
    }

    private static String clip(String text, int max, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String t = text.strip();
        if (t.length() <= max) {
            return t;
        }
        int end = max;
        if (Character.isHighSurrogate(t.charAt(end - 1))) {
            end--;
        }
        return t.substring(0, end) + "…";
    }
}
