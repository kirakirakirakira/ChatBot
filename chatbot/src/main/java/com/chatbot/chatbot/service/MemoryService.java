package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.config.MemoryProperties;
import com.chatbot.chatbot.dto.CreateMemoryRequest;
import com.chatbot.chatbot.dto.UpdateMemoryRequest;
import com.chatbot.chatbot.dto.UserMemoryVO;
import com.chatbot.chatbot.entity.MemoryKind;
import com.chatbot.chatbot.entity.MemorySource;
import com.chatbot.chatbot.entity.MemoryStatus;
import com.chatbot.chatbot.entity.UserMemory;
import com.chatbot.chatbot.repository.UserMemoryRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆的读写入口。所有方法的目标用户都只来自 CurrentUser，绝不接受请求体里的 userId。
 * <p>
 * 自动抽取走 {@link MemoryStoreService}，但它复用这里的规范化 / 去重 / 敏感信息规则，
 * 保证「手动能加什么、模型就能存什么」。
 */
@Service
public class MemoryService {

    private final UserMemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final MemoryProperties properties;

    public MemoryService(UserMemoryRepository memoryRepository,
                         UserRepository userRepository,
                         MemoryProperties properties) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /** 全局总开关；ChatService 用它决定要不要把已有会话摘要也停掉。 */
    public boolean globalEnabled() {
        return properties.enabled();
    }

    @Transactional(readOnly = true)
    public List<UserMemoryVO> list(CurrentUser user) {
        return memoryRepository.findActive(user.id(), PageRequest.of(0, Math.max(1, properties.maxActive())))
                .stream()
                .map(UserMemoryVO::from)
                .toList();
    }

    @Transactional
    public UserMemoryVO create(CurrentUser user, CreateMemoryRequest request) {
        String content = requireContent(request.content());
        String kind = requireKind(request.kind());
        String hash = MemoryText.hash(content);

        UserMemory existing = memoryRepository.findByUserIdAndContentHash(user.id(), hash).orElse(null);
        if (existing != null) {
            // 相同内容再次添加视为「重新确认」：复活并转成手动，避免唯一键冲突把用户挡在门外
            existing.setKind(kind);
            existing.setContent(content);
            existing.setSource(MemorySource.MANUAL.code());
            existing.setConfidence(BigDecimal.ONE);
            existing.setStatus(MemoryStatus.ACTIVE.code());
            return UserMemoryVO.from(memoryRepository.save(existing));
        }

        long active = memoryRepository.countByUserIdAndStatus(user.id(), MemoryStatus.ACTIVE.code());
        if (active >= properties.maxActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "记忆数量已达上限 " + properties.maxActive() + " 条，请先删除不用的记忆");
        }

        UserMemory memory = new UserMemory();
        memory.setUser(userRepository.getReferenceById(user.id()));
        memory.setKind(kind);
        memory.setContent(content);
        memory.setContentHash(hash);
        memory.setSource(MemorySource.MANUAL.code());
        memory.setConfidence(BigDecimal.ONE);
        memory.setStatus(MemoryStatus.ACTIVE.code());
        return UserMemoryVO.from(memoryRepository.save(memory));
    }

    @Transactional
    public UserMemoryVO update(CurrentUser user, Long id, UpdateMemoryRequest request) {
        UserMemory memory = requireOwned(user, id);
        String content = requireContent(request.content());
        String kind = requireKind(request.kind());
        String hash = MemoryText.hash(content);

        memoryRepository.findByUserIdAndContentHash(user.id(), hash)
                .filter(other -> !other.getId().equals(memory.getId()))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已存在相同内容的记忆");
                });

        memory.setKind(kind);
        memory.setContent(content);
        memory.setContentHash(hash);
        memory.setSource(MemorySource.MANUAL.code());
        memory.setConfidence(BigDecimal.ONE);
        memory.setStatus(MemoryStatus.ACTIVE.code());
        return UserMemoryVO.from(memoryRepository.save(memory));
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        memoryRepository.delete(requireOwned(user, id));
    }

    @Transactional
    public void clear(CurrentUser user) {
        memoryRepository.deleteByUserId(user.id());
    }

    /**
     * 取本轮要注入 Prompt 的记忆。
     * <p>
     * 先按最近更新时间取一批，再按 token 预算截断。MVP 不做向量检索：
     * 单用户最多 100 条短事实，把最近 20 条塞进 system 已经足够；等记忆量真的上来再加 embedding。
     * <p>
     * 这里顺带记一次使用次数（一条批量 UPDATE），以后做衰减 / 清理时有数据可用。
     */
    @Transactional
    public List<UserMemory> promptMemories(CurrentUser user) {
        if (!properties.enabled() || !user.memoryEnabled()) {
            return List.of();
        }
        List<UserMemory> candidates = memoryRepository.findActive(
                user.id(), PageRequest.of(0, Math.max(1, properties.injectLimit())));
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UserMemory> picked = new ArrayList<>();
        int tokens = 0;
        for (UserMemory memory : candidates) {
            int cost = MemoryText.estimateTokens(memory.getContent()) + 8;
            if (!picked.isEmpty() && tokens + cost > properties.injectMaxTokens()) {
                break;
            }
            picked.add(memory);
            tokens += cost;
        }
        memoryRepository.touchUsed(picked.stream().map(UserMemory::getId).toList(), LocalDateTime.now());
        return picked;
    }

    private UserMemory requireOwned(CurrentUser user, Long id) {
        return memoryRepository.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记忆不存在: " + id));
    }

    private static String requireKind(String raw) {
        try {
            return MemoryKind.from(raw).name();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String requireContent(String raw) {
        String content = MemoryText.normalize(raw);
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记忆内容不能为空");
        }
        if (MemoryText.hasSecret(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "记忆不能包含密码、密钥、身份证或银行卡号");
        }
        return content;
    }
}
