package com.chatbot.chatbot.service;

import com.chatbot.chatbot.config.MemoryProperties;
import com.chatbot.chatbot.entity.MemoryKind;
import com.chatbot.chatbot.entity.MemorySource;
import com.chatbot.chatbot.entity.MemoryStatus;
import com.chatbot.chatbot.entity.UserMemory;
import com.chatbot.chatbot.repository.UserMemoryRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * 自动抽取结果的落库层。LLM 调用在 {@link MemoryExtractionService} 里完成，
 * 这里只做校验、去重和短事务写入——绝不在持有数据库事务时等模型返回。
 */
@Service
public class MemoryStoreService {

    private static final Logger log = LoggerFactory.getLogger(MemoryStoreService.class);

    private final UserMemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final MemoryProperties properties;

    public MemoryStoreService(UserMemoryRepository memoryRepository,
                              UserRepository userRepository,
                              MemoryProperties properties) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional
    public ApplyResult apply(Long userId, Long conversationId, Long messageId, List<ExtractedMemory> items) {
        if (items == null || items.isEmpty()) {
            return new ApplyResult(0, 0, 0, 0);
        }
        int added = 0;
        int updated = 0;
        int deleted = 0;
        int skipped = 0;

        for (ExtractedMemory item : items.stream().limit(Math.max(1, properties.maxPerTurn())).toList()) {
            String action = item.action() == null ? "" : item.action().strip().toUpperCase(Locale.ROOT);
            switch (action) {
                case "ADD" -> {
                    Result result = add(userId, conversationId, messageId, item);
                    if (result == Result.ADDED) {
                        added++;
                    } else if (result == Result.UPDATED) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
                case "UPDATE" -> {
                    if (update(userId, conversationId, messageId, item)) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
                case "DELETE" -> {
                    if (delete(userId, item)) {
                        deleted++;
                    } else {
                        skipped++;
                    }
                }
                default -> skipped++;
            }
        }
        if (added + updated + deleted > 0) {
            log.debug("记忆落库 userId={} added={} updated={} deleted={} skipped={}",
                    userId, added, updated, deleted, skipped);
        }
        return new ApplyResult(added, updated, deleted, skipped);
    }

    private Result add(Long userId, Long conversationId, Long messageId, ExtractedMemory item) {
        String content = sanitize(item);
        if (content == null) {
            return Result.SKIPPED;
        }
        String kind = kindOf(item.kind());
        if (kind == null) {
            return Result.SKIPPED;
        }
        BigDecimal confidence = confidenceOf(item.confidence());
        if (confidence.compareTo(BigDecimal.valueOf(properties.minConfidence())) < 0) {
            return Result.SKIPPED;
        }

        String hash = MemoryText.hash(content);
        UserMemory existing = memoryRepository.findByUserIdAndContentHash(userId, hash).orElse(null);
        if (existing != null) {
            // 用户手动编辑过的记忆不允许被自动抽取覆盖；这是「用户说了算」的硬边界
            if (isManual(existing)) {
                return Result.SKIPPED;
            }
            existing.setKind(kind);
            existing.setContent(content);
            existing.setSource(MemorySource.AUTO.code());
            existing.setConfidence(confidence);
            existing.setStatus(MemoryStatus.ACTIVE.code());
            existing.setSourceConversationId(conversationId);
            existing.setSourceMessageId(messageId);
            memoryRepository.save(existing);
            return Result.UPDATED;
        }

        long active = memoryRepository.countByUserIdAndStatus(userId, MemoryStatus.ACTIVE.code());
        if (active >= properties.maxActive()) {
            return Result.SKIPPED;
        }

        UserMemory memory = new UserMemory();
        memory.setUser(userRepository.getReferenceById(userId));
        memory.setKind(kind);
        memory.setContent(content);
        memory.setContentHash(hash);
        memory.setSource(MemorySource.AUTO.code());
        memory.setConfidence(confidence);
        memory.setStatus(MemoryStatus.ACTIVE.code());
        memory.setSourceConversationId(conversationId);
        memory.setSourceMessageId(messageId);
        memoryRepository.save(memory);
        return Result.ADDED;
    }

    private boolean update(Long userId, Long conversationId, Long messageId, ExtractedMemory item) {
        if (item.id() == null) {
            return false;
        }
        UserMemory memory = memoryRepository.findByIdAndUserId(item.id(), userId).orElse(null);
        if (memory == null || isManual(memory)) {
            return false;
        }
        String content = sanitize(item);
        String kind = kindOf(item.kind());
        if (content == null || kind == null) {
            return false;
        }
        String hash = MemoryText.hash(content);
        UserMemory other = memoryRepository.findByUserIdAndContentHash(userId, hash).orElse(null);
        if (other != null && !other.getId().equals(memory.getId())) {
            return false;
        }
        memory.setKind(kind);
        memory.setContent(content);
        memory.setContentHash(hash);
        memory.setSource(MemorySource.AUTO.code());
        memory.setConfidence(confidenceOf(item.confidence()));
        memory.setStatus(MemoryStatus.ACTIVE.code());
        memory.setSourceConversationId(conversationId);
        memory.setSourceMessageId(messageId);
        memoryRepository.save(memory);
        return true;
    }

    private boolean delete(Long userId, ExtractedMemory item) {
        if (item.id() == null) {
            return false;
        }
        UserMemory memory = memoryRepository.findByIdAndUserId(item.id(), userId).orElse(null);
        if (memory == null || isManual(memory)) {
            return false;
        }
        memoryRepository.delete(memory);
        return true;
    }

    private static String sanitize(ExtractedMemory item) {
        String content = MemoryText.normalize(item.content());
        if (content.isEmpty() || MemoryText.hasSecret(content)) {
            return null;
        }
        return content;
    }

    private static String kindOf(String raw) {
        try {
            return MemoryKind.from(raw).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal confidenceOf(Double raw) {
        double value = raw == null ? 0.7 : raw;
        value = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isManual(UserMemory memory) {
        return memory.getSource() != null && memory.getSource() == MemorySource.MANUAL.code();
    }

    public record ApplyResult(int added, int updated, int deleted, int skipped) {
    }

    private enum Result {
        ADDED,
        UPDATED,
        SKIPPED
    }
}
