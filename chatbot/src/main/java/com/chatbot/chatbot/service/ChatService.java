package com.chatbot.chatbot.service;

import com.chatbot.chatbot.dto.ChatEvent;
import com.chatbot.chatbot.dto.ChatRequest;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.llm.LlmClient;
import com.chatbot.chatbot.llm.LlmMessage;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ChatService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatService(ConversationService conversationService,
                       ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       LlmClient llmClient,
                       ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 发消息并流式返回 AI 回复：
     * 1. 用户消息入库
     * 2. 取全部历史作为上下文调用 LLM
     * 3. 每个 token 通过 SSE(delta) 推给前端
     * 4. 结束后助手消息入库，推 done 事件
     */
    public SseEmitter chat(Long conversationId, ChatRequest request) {
        Conversation conversation = conversationService.require(conversationId);
        saveMessage(conversation, Role.USER, request.message());

        List<LlmMessage> history = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId())
                .stream()
                .map(m -> new LlmMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.submit(() -> {
            StringBuilder full = new StringBuilder();
            try {
                llmClient.streamChat(history, token -> {
                    full.append(token);
                    send(emitter, ChatEvent.delta(token));
                });
                Message saved = saveMessage(conversation, Role.ASSISTANT, full.toString());
                send(emitter, ChatEvent.done(saved.getId()));
                emitter.complete();
            } catch (Exception e) {
                // 已生成的部分也入库，避免丢失
                if (!full.isEmpty()) {
                    try {
                        saveMessage(conversation, Role.ASSISTANT, full.toString());
                    } catch (Exception ignored) {
                    }
                }
                try {
                    send(emitter, ChatEvent.error(String.valueOf(e.getMessage())));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void send(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            throw new IllegalStateException("SSE 发送失败（客户端可能已断开）", e);
        }
    }

    private Message saveMessage(Conversation conversation, Role role, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message = messageRepository.save(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        return message;
    }
}
