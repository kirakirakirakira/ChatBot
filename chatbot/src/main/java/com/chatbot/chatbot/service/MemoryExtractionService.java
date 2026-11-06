package com.chatbot.chatbot.service;

import com.chatbot.chatbot.config.MemoryProperties;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.UserMemory;
import com.chatbot.chatbot.llm.LlmCallOptions;
import com.chatbot.chatbot.llm.LlmMessage;
import com.chatbot.chatbot.llm.LlmProperties;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从一轮对话里抽取长期记忆。
 * <p>
 * 全流程失败都不影响聊天：调用方在 SSE 已经 done 之后才提交这个任务，
 * 这里任何异常只记日志，不向用户抛。
 */
@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    private final MessageRepository messageRepository;
    private final UserMemoryRepository memoryRepository;
    private final LlmProperties llmProperties;
    private final MemoryProperties memoryProperties;
    private final LlmTextService llmTextService;
    private final MemoryStoreService memoryStoreService;
    private final ObjectMapper objectMapper;

    public MemoryExtractionService(MessageRepository messageRepository,
                                   UserMemoryRepository memoryRepository,
                                   LlmProperties llmProperties,
                                   MemoryProperties memoryProperties,
                                   LlmTextService llmTextService,
                                   MemoryStoreService memoryStoreService,
                                   ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.memoryRepository = memoryRepository;
        this.llmProperties = llmProperties;
        this.memoryProperties = memoryProperties;
        this.llmTextService = llmTextService;
        this.memoryStoreService = memoryStoreService;
        this.objectMapper = objectMapper;
    }

    public void extractAndStore(Long userId, Long conversationId, Long userMessageId, Long assistantMessageId) {
        if (!memoryProperties.enabled()) {
            return;
        }
        String model = UtilityModel.resolve(llmProperties, memoryProperties.extractionModel());
        if (model == null) {
            return;
        }
        try {
            List<Message> recent = messageRepository.findByConversationId(
                    conversationId, PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "id")));
            Collections.reverse(recent);
            List<UserMemory> existing = memoryRepository.findActive(
                    userId, PageRequest.of(0, 20));

            String system = buildSystemPrompt(existing);
            String conversation = formatConversation(recent);
            String raw = llmTextService.complete(
                    List.of(LlmMessage.of("system", system), LlmMessage.of("user", conversation)),
                    new LlmCallOptions(model, false, null, false));
            List<ExtractedMemory> items = parse(raw);
            if (items.isEmpty()) {
                return;
            }
            // 来源记用户消息而不是助手回答：记忆是用户说的事实，助手只是抽取器
            MemoryStoreService.ApplyResult result = memoryStoreService.apply(
                    userId, conversationId, userMessageId, items);
            log.debug("记忆抽取完成 userId={} conversationId={} userMessageId={} assistantMessageId={} result={}",
                    userId, conversationId, userMessageId, assistantMessageId, result);
        } catch (Exception e) {
            // 抽取是锦上添花，任何失败都不该冒泡到 SSE
            log.warn("记忆抽取失败 userId={} conversationId={}: {}", userId, conversationId, describe(e));
        }
    }

    private String buildSystemPrompt(List<UserMemory> existing) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是记忆抽取器。只提取用户明确说过的、跨会话仍然有用的长期信息，最多 ")
                .append(Math.max(1, memoryProperties.maxPerTurn())).append(" 条。\n")
                .append("不要提取：一次性任务、临时问题、模型自己的推测，以及密码、token、身份证、银行卡、健康、宗教、政治等敏感信息。\n")
                .append("kind 只能是 PREFERENCE / FACT / PROJECT / CONSTRAINT。\n")
                .append("如果新信息与已有记忆冲突，用 UPDATE 覆盖旧条目；用户明确要求忘记某条时用 DELETE。\n")
                .append("已有记忆：\n");
        if (existing.isEmpty()) {
            sb.append("（无）\n");
        } else {
            for (UserMemory memory : existing) {
                sb.append("- id=").append(memory.getId())
                        .append(" kind=").append(memory.getKind())
                        .append(" content=").append(memory.getContent())
                        .append('\n');
            }
        }
        sb.append("\n输出严格 JSON，不要 Markdown 代码块：\n")
                .append("{\"memories\":[{\"action\":\"ADD\",\"id\":null,\"kind\":\"PREFERENCE\","
                        + "\"content\":\"...\",\"confidence\":0.9}]}");
        return sb.toString();
    }

    private static String formatConversation(List<Message> messages) {
        StringBuilder sb = new StringBuilder("本轮对话：\n");
        for (Message message : messages) {
            String content = message.getContent() == null ? "" : message.getContent();
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "…";
            }
            sb.append('[').append(message.getRole().name()).append("] ").append(content).append('\n');
        }
        return sb.toString();
    }

    private List<ExtractedMemory> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
            JsonNode memories = root.path("memories");
            if (!memories.isArray()) {
                return List.of();
            }
            List<ExtractedMemory> out = new ArrayList<>();
            for (JsonNode node : memories) {
                String action = text(node, "action");
                if (action == null) {
                    continue;
                }
                Long id = node.path("id").isNumber() ? node.path("id").asLong() : null;
                out.add(new ExtractedMemory(
                        action,
                        id,
                        text(node, "kind"),
                        text(node, "content"),
                        node.path("confidence").isNumber() ? node.path("confidence").asDouble() : 0.7));
                if (out.size() >= Math.max(1, memoryProperties.maxPerTurn())) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            // 不打印原文：它可能包含用户隐私
            log.debug("记忆抽取返回无法解析为 JSON，已丢弃");
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().strip() : null;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }
}
