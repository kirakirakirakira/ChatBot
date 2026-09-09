package com.chatbot.chatbot.service;

import com.chatbot.chatbot.dto.ChatEvent;
import com.chatbot.chatbot.dto.ChatRequest;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.llm.LlmClient;
import com.chatbot.chatbot.llm.LlmMessage;
import com.chatbot.chatbot.llm.LlmProperties;
import com.chatbot.chatbot.llm.LlmStreamListener;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** SSE 超时相对模型超时的余量（原因见下方 sseTimeout()）。 */
    private static final long SSE_TIMEOUT_MARGIN_MS = 30_000L;

    /** ConversationService.create() 写入的默认标题；只有还等于它时才自动改名。 */
    private static final String DEFAULT_TITLE = "新的对话";
    private static final int TITLE_MAX_LENGTH = 30;

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * SSE 超时由模型超时推导，只留一个旋钮：写死固定值时，长思考会让两边几乎同时到点，
     * 用户看到的是连接莫名断掉。
     */
    private final long sseTimeoutMs;

    public ChatService(ConversationService conversationService,
                       ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       LlmClient llmClient,
                       LlmProperties llmProperties,
                       ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.sseTimeoutMs = llmProperties.requestTimeoutSeconds() * 1000L + SSE_TIMEOUT_MARGIN_MS;
    }

    /**
     * 发消息并流式返回 AI 回复：
     * 1. 会话还叫默认标题时，用首条用户消息前 30 字自动起标题
     * 2. 用户消息入库
     * 3. 取最近 N 条历史（llm.max-history-messages）作为上下文调用 LLM
     * 4. 思考增量走 SSE reasoning，回答增量走 SSE delta
     * 5. 结束后助手消息入库（只存回答，不存思考），推 done
     * <p>
     * 取消：前端 abort（点停止 / 关页面 / 切会话）、SSE 超时、客户端断网都会置 cancelled，
     * 正在跑的生成在下一个增量立刻停下（思考增量也算，否则思考那几分钟里点停止停不下来）；
     * 已生成的回答照样入库，不会因为中断丢掉。
     */
    public SseEmitter chat(Long conversationId, ChatRequest request) {
        Conversation conversation = conversationService.require(conversationId);

        applyAutoTitle(conversation, request.message());
        saveMessage(conversation, Role.USER, request.message());

        List<LlmMessage> history = recentHistory(conversation.getId());

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onTimeout(() -> {
            cancelled.set(true);
            log.debug("SSE 超时，停止生成 conversationId={}", conversationId);
        });
        emitter.onError(e -> cancelled.set(true));
        emitter.onCompletion(() -> cancelled.set(true));

        executor.submit(() -> stream(conversation, emitter, cancelled, history, request.enableThinking()));
        return emitter;
    }

    private void stream(Conversation conversation, SseEmitter emitter,
                        AtomicBoolean cancelled, List<LlmMessage> history,
                        Boolean enableThinking) {
        StringBuilder full = new StringBuilder();
        // 思考和回答各用一个代理字符暂存区，两条流的切分点互不影响。
        SurrogateBuffer contentBuffer = new SurrogateBuffer();
        SurrogateBuffer reasoningBuffer = new SurrogateBuffer();
        // 思考只透传给前端、不入库：助手消息正文只该是正式回答；计数用于排查思考远超回答的成本异常
        AtomicLong reasoningChars = new AtomicLong();
        try {
            llmClient.streamChat(history, enableThinking, new LlmStreamListener() {
                @Override
                public void onReasoning(String text) {
                    checkCancelled(cancelled);
                    reasoningChars.addAndGet(text.length());
                    String chunk = reasoningBuffer.feed(text);
                    if (!chunk.isEmpty()) {
                        send(emitter, ChatEvent.reasoning(chunk));
                    }
                }

                @Override
                public void onToken(String text) {
                    checkCancelled(cancelled);
                    full.append(text);
                    String chunk = contentBuffer.feed(text);
                    if (!chunk.isEmpty()) {
                        send(emitter, ChatEvent.delta(chunk));
                    }
                }
            });
            if (contentBuffer.hasHeld() || reasoningBuffer.hasHeld()) {
                log.debug("流结束时仍有落单的高位代理字符，已跳过推送 conversationId={}", conversation.getId());
            }
            Message saved = saveMessage(conversation, Role.ASSISTANT, full.toString());
            send(emitter, ChatEvent.done(saved.getId()));
            emitter.complete();
            if (reasoningChars.get() > 0) {
                log.debug("思考过程共 {} 字，已推送前端、未入库 conversationId={}",
                        reasoningChars.get(), conversation.getId());
            }
        } catch (StreamAbortedException e) {
            // 用户停止 / 页面关闭 / 超时：保留已生成内容，不再往前端写任何东西
            savePartial(conversation, full, "生成被中断");
            completeQuietly(emitter);
        } catch (Exception e) {
            log.warn("对话生成失败 conversationId={}", conversation.getId(), e);
            savePartial(conversation, full, "生成失败");
            // 错误已经用 SSE 事件告诉前端了，这里正常 complete 即可：
            // completeWithError(e) 会让 Spring 把异常抛回已提交的 text/event-stream，日志多一条没意义的 IllegalStateException
            sendQuietly(emitter, ChatEvent.error(describe(e)));
            completeQuietly(emitter);
        }
    }

    /**
     * 已取消就抛异常，让上层保留已生成内容。
     * 思考和回答两条流都要检查：只在回答流里检查的话，思考阶段点「停止」要等到回答开始才生效。
     */
    private static void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw new StreamAbortedException();
        }
    }

    /** 推一个事件；客户端已断开就转成 StreamAbortedException，让上层保留已生成内容。 */
    private void send(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            throw new StreamAbortedException();
        }
    }

    private void sendQuietly(SseEmitter emitter, ChatEvent event) {
        try {
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
        } catch (Exception ignored) {
            // 客户端可能已经断开，发不出去就算了
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已经 complete 过 / 连接已断，忽略
        }
    }

    private void savePartial(Conversation conversation, StringBuilder full, String reason) {
        if (full.isEmpty()) {
            return;
        }
        try {
            saveMessage(conversation, Role.ASSISTANT, full.toString());
            log.debug("{}，已保存部分回复 conversationId={}", reason, conversation.getId());
        } catch (Exception e) {
            log.warn("{}，保存部分回复失败 conversationId={}", reason, conversation.getId(), e);
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

    /** 只取最近 N 条历史，避免长会话把上下文窗口和 token 一起撑爆。 */
    private List<LlmMessage> recentHistory(Long conversationId) {
        List<Message> all = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        int limit = llmProperties.maxHistoryMessages();
        List<Message> window = (limit > 0 && all.size() > limit)
                ? all.subList(all.size() - limit, all.size())
                : all;
        return window.stream()
                .map(m -> new LlmMessage(m.getRole().name().toLowerCase(), m.getContent()))
                .toList();
    }

    /**
     * 用首条用户消息给会话起标题，否则侧边栏永远是一排「新的对话」。
     * 只改内存字段，紧接着 saveMessage() 里的 save() 会一并落库。
     */
    private void applyAutoTitle(Conversation conversation, String userMessage) {
        String current = conversation.getTitle();
        if (current != null && !current.isBlank() && !DEFAULT_TITLE.equals(current)) {
            return;
        }
        String title = userMessage.strip().replaceAll("\\s+", " ");
        if (title.isEmpty()) {
            return;
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH) + "…";
        }
        conversation.setTitle(title);
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 落单高位代理字符的暂存区：emoji 等非 BMP 字符在 UTF-16 里占 2 个 char，
     * 正好被切成两段增量时，单个 char 编不出 UTF-8，Jackson 会写成 ?，前端看到乱码。
     * 只在单个生成任务内使用，不需要线程安全。
     */
    private static final class SurrogateBuffer {

        private final StringBuilder held = new StringBuilder();

        /** 喂进一段增量，返回现在可以安全推送的部分（可能是空串）。 */
        String feed(String token) {
            String chunk = held.toString() + token;
            held.setLength(0);
            if (!chunk.isEmpty() && Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))) {
                held.append(chunk.charAt(chunk.length() - 1));
                chunk = chunk.substring(0, chunk.length() - 1);
            }
            return chunk;
        }

        boolean hasHeld() {
            return !held.isEmpty();
        }
    }

    /** 客户端断开或主动停止，用来中断正在进行的 LLM 流式读取。 */
    private static final class StreamAbortedException extends RuntimeException {
    }
}
