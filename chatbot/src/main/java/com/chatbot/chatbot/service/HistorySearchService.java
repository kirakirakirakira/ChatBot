package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「第二大脑」的检索层：跨会话搜自己的历史消息 / 列出自己的会话 / 展开某段上下文。
 * <p>
 * 所有入口都接收 userId（来自 CurrentUser 的 id），SQL 一律带 owner 条件；
 * 这是本功能唯一的越权风险点，不能把归属校验交给调用方。
 * <p>
 * 刻意不加 @Transactional：全文检索可能因为索引缺席抛 SQL 异常，若整段包在一个事务里，
 * 失败的那次查询会把事务标成 rollback-only，后面的 LIKE 兜底也跑不了。
 * 这里每条查询各自自动提交，失败就换一条路。
 */
@Service
public class HistorySearchService {

    private static final Logger log = LoggerFactory.getLogger(HistorySearchService.class);

    private static final int MAX_MESSAGE_HITS = 20;
    private static final int MAX_CONVERSATION_HITS = 5;
    private static final int SNIPPET_CHARS = 180;
    private static final int SNIPPET_LEAD = 40;
    private static final int MAX_READ_MESSAGES = 40;
    private static final int MESSAGE_CLIP_CHARS = 1200;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;

    public HistorySearchService(MessageRepository messageRepository,
                                ConversationRepository conversationRepository,
                                ConversationService conversationService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
    }

    /** 一条消息级命中。content 不返回给调用方，只带摘好的 snippet。 */
    public record MessageHit(Long messageId, Long conversationId, String conversationTitle,
                             String role, String snippet, LocalDateTime createdAt) {
    }

    /** 一个会话级命中：标题 / 摘要命中，但正文里未必有原词，所以不带 messageId。 */
    public record ConversationHit(Long conversationId, String title, String summary,
                                  LocalDateTime updatedAt) {
    }

    public record SearchResult(List<MessageHit> messages, List<ConversationHit> conversations) {
    }

    /** 读一段会话上下文。items 已按 id 正序，直接喂给模型 / 前端。 */
    public record ConversationWindow(Long conversationId, String title, List<Message> messages) {
    }

    /**
     * 主检索：先消息、再会话。limit 只约束消息条数；会话级命中最多 5 条，够模型判断方向即可。
     */
    public SearchResult search(Long userId, Long excludeConversationId, String query, int limit) {
        int size = clamp(limit, 1, MAX_MESSAGE_HITS);
        long exclude = excludeConversationId == null ? -1L : excludeConversationId;
        List<MessageHit> messages = searchMessages(userId, exclude, query, size);
        List<ConversationHit> conversations = searchConversations(userId, exclude, query,
                Math.min(MAX_CONVERSATION_HITS, size));
        return new SearchResult(messages, conversations);
    }

    private List<MessageHit> searchMessages(Long userId, long excludeConversationId,
                                            String query, int limit) {
        List<Object[]> rows;
        try {
            rows = messageRepository.searchOwnedFullText(userId, excludeConversationId, query, limit);
        } catch (DataAccessException e) {
            // 索引还没建好、或库不支持 ngram：不中断对话，退回 LIKE
            log.debug("历史全文检索不可用，回退 LIKE: {}", e.getMessage());
            rows = List.of();
        }
        // ngram 是「任意 bigram 命中」就返回，像「检索」这种常见词会把不相干的消息带进来。
        // 这里做一道相关性过滤：要么包含完整查询，要么覆盖足够多的查询 bigram。
        if (!rows.isEmpty()) {
            rows = rows.stream().filter(row -> relevant(asString(row[4]), query)).toList();
        }
        if (rows.isEmpty()) {
            // 全文没命中（或被相关性过滤掉）时走一次 LIKE：ngram_token_size=2 会漏掉单字查询，
            // 而中文用户经常只输一个字；精确子串也更容易命中用户记得的原话。
            rows = messageRepository.searchOwnedLike(userId, excludeConversationId, query, limit);
        }
        List<MessageHit> hits = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String content = asString(row[4]);
            hits.add(new MessageHit(
                    asLong(row[0]),
                    asLong(row[1]),
                    asString(row[2]),
                    asString(row[3]),
                    snippet(content, query, SNIPPET_CHARS),
                    asDateTime(row[5])));
        }
        return hits;
    }

    private List<ConversationHit> searchConversations(Long userId, long excludeConversationId,
                                                      String query, int limit) {
        List<Conversation> rows = conversationRepository.searchOwnedByKeyword(
                userId, excludeConversationId, query, PageRequest.of(0, limit));
        List<ConversationHit> hits = new ArrayList<>(rows.size());
        for (Conversation c : rows) {
            String summary = c.getSummary();
            hits.add(new ConversationHit(
                    c.getId(),
                    c.getTitle(),
                    summary == null || summary.isBlank()
                            ? "" : snippet(summary, query, SNIPPET_CHARS),
                    c.getUpdatedAt()));
        }
        return hits;
    }

    /** 列出当前用户的会话，可按标题过滤；只返回元信息，正文由 readConversation 再取。 */
    public List<Conversation> listConversations(Long userId, String keyword, int limit) {
        int size = clamp(limit, 1, 30);
        PageRequest page = PageRequest.of(0, size);
        String kw = keyword == null ? "" : keyword.strip();
        if (kw.isEmpty()) {
            return conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(userId, page);
        }
        return conversationRepository.findByOwnerIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                userId, kw, page);
    }

    /**
     * 读一段会话。aroundMessageId 为空时取最新 limit 条；否则以它为中心取前后各一半。
     * 归属校验在 ConversationService.requireOwned —— 不是自己的会话一律 404。
     */
    public ConversationWindow readConversation(Long conversationId, CurrentUser user,
                                               Long aroundMessageId, int limit) {
        Conversation conversation = conversationService.requireOwned(conversationId, user);
        int size = clamp(limit, 2, MAX_READ_MESSAGES);
        List<Message> rows;
        if (aroundMessageId == null) {
            rows = new ArrayList<>(messageRepository.findByConversationId(conversationId,
                    PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "id"))));
            Collections.reverse(rows);
        } else {
            if (messageRepository.findByIdAndConversationId(aroundMessageId, conversationId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "消息不属于该会话: " + aroundMessageId);
            }
            int olderSize = Math.max(1, size / 2);
            List<Message> older = new ArrayList<>(messageRepository.findByConversationIdAndIdLessThan(
                    conversationId, aroundMessageId + 1,
                    PageRequest.of(0, olderSize, Sort.by(Sort.Direction.DESC, "id"))));
            Collections.reverse(older);
            List<Message> newer = messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(
                    conversationId, aroundMessageId, PageRequest.of(0, size - older.size()));
            rows = new ArrayList<>(older.size() + newer.size());
            rows.addAll(older);
            rows.addAll(newer);
        }
        return new ConversationWindow(conversation.getId(), conversation.getTitle(), rows);
    }

    /** 给模型看的正文片段；单条太长会撑爆 agent.max-result-chars，截断是必须的。 */
    public static String clipForModel(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        if (t.length() <= maxChars) {
            return t;
        }
        int end = maxChars;
        if (Character.isHighSurrogate(t.charAt(end - 1))) {
            end--;
        }
        return t.substring(0, end) + "…";
    }

    public static int clipLimit() {
        return MESSAGE_CLIP_CHARS;
    }

    /**
     * 从正文里截一段「关键词附近」的摘要。
     * 查询按空格拆 token，取最早出现的位置；一个都没命中（全文是按 ngram 命中的子串）就从头截。
     * 折叠所有空白：模型和卡片都不需要消息里的换行排版。
     */
    static String snippet(String content, String query, int maxChars) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = content.replaceAll("\\s+", " ").strip();
        int at = -1;
        if (query != null) {
            for (String token : query.split("\\s+")) {
                String t = token.strip();
                if (t.length() < 2) {
                    continue;
                }
                int i = text.toLowerCase().indexOf(t.toLowerCase());
                if (i >= 0 && (at < 0 || i < at)) {
                    at = i;
                }
            }
        }
        if (at < 0) {
            at = 0;
        }
        int start = Math.max(0, at - SNIPPET_LEAD);
        // 起止都不能落在代理对中间，否则前端拿到的是半个 emoji，SSE / JSON 都会出问题
        if (start > 0 && Character.isLowSurrogate(text.charAt(start))) {
            start++;
        }
        int end = Math.min(text.length(), start + maxChars);
        if (end < text.length() && end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        String out = text.substring(start, end);
        return (start > 0 ? "…" : "") + out + (end < text.length() ? "…" : "");
    }

    /**
     * 相关性判断。规则刻意保守：宁可少返回，也不要把「只命中一个常见词」的消息当成用户要找的历史。
     * 含空格的查询按 token 任一命中；单个中文长串按 bigram 覆盖率 >= 60%。
     */
    static boolean relevant(String content, String query) {
        if (content == null || query == null) {
            return false;
        }
        String text = content.toLowerCase();
        String q = query.strip().toLowerCase();
        if (q.isEmpty()) {
            return false;
        }
        if (text.contains(q)) {
            return true;
        }
        String[] tokens = q.split("\\s+");
        if (tokens.length > 1) {
            for (String token : tokens) {
                if (token.length() >= 2 && text.contains(token)) {
                    return true;
                }
            }
            return false;
        }
        if (q.length() <= 2) {
            return text.contains(q);
        }
        int total = 0;
        int hit = 0;
        for (int i = 0; i + 2 <= q.length(); i++) {
            String bigram = q.substring(i, i + 2);
            if (bigram.isBlank()) {
                continue;
            }
            total++;
            if (text.contains(bigram)) {
                hit++;
            }
        }
        return total == 0 || (double) hit / total >= 0.6;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dt) {
            return dt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
