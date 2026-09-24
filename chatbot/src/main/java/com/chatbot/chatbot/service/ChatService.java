package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.ChatEvent;
import com.chatbot.chatbot.dto.ChatRequest;
import com.chatbot.chatbot.dto.RegenerateRequest;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.entity.Message;
import com.chatbot.chatbot.entity.Role;
import com.chatbot.chatbot.llm.LlmCallOptions;
import com.chatbot.chatbot.llm.LlmClient;
import com.chatbot.chatbot.llm.LlmMessage;
import com.chatbot.chatbot.llm.LlmProperties;
import com.chatbot.chatbot.llm.LlmStreamListener;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * thinking_budget 的界面/接口上限。取 qwen3.8 系的「最大思维链长度」262144
     * （raw/model-user-guide/.../qwen3-8-max.md）；超过模型自身上限时百炼返回 400 并在文案里写明该模型的上限。
     */
    private static final int MAX_THINKING_BUDGET = 262144;

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
     * <p>
     * 会话归属在这里校验（requireOwned）：往别人的会话里发消息和读别人的会话是同一种越权，一律 404。
     * 校验必须发生在提交异步任务之前——SSE 一旦开始写响应，状态码就锁死成 200 了，再想报 404 已经晚了。
     */
    public SseEmitter chat(Long conversationId, ChatRequest request, CurrentUser user) {
        Conversation conversation = conversationService.requireOwned(conversationId, user);

        applyAutoTitle(conversation, request.message());
        saveMessage(conversation, Role.USER, request.message(), null);

        LlmCallOptions options = buildOptions(request.enableThinking(), request.model(), null, request.thinkingBudget());
        return startStream(conversation, recentHistory(conversation.getId()), options);
    }

    /**
     * 重新生成：删掉最后一条助手消息，用它前面那条用户消息重跑一遍生成。
     * <p>
     * 归属校验和 chat 同一个口径（requireOwned），同样必须在提交异步任务之前完成。
     * 删除发生在 service 层（dropLastAssistantMessage），删完再取历史，
     * 所以重跑时的上下文里自然不含被删掉的旧回答。
     */
    public SseEmitter regenerate(Long conversationId, RegenerateRequest request, CurrentUser user) {
        Conversation conversation = conversationService.requireOwned(conversationId, user);
        ConversationService.DroppedReply dropped = conversationService.dropLastAssistantMessage(conversationId);
        // 没指定模型就沿用被删那条回答的模型：用户当初选了什么，重跑就该还是什么
        String requestedModel = (request != null && request.model() != null && !request.model().isBlank())
                ? request.model() : dropped.model();
        LlmCallOptions options = buildOptions(
                (request == null) ? null : request.enableThinking(),
                requestedModel,
                null,
                (request == null) ? null : request.thinkingBudget());
        return startStream(conversation, recentHistory(conversation.getId()), options);
    }

    /**
     * 组装单次调用选项，顺带做两道校验：
     * 模型必须落在 llm.available-models 白名单里；思考预算必须是正整数且不超过上限，
     * 并且**思考关着时预算直接丢弃**（下发一个不生效的参数只会制造困惑）。
     *
     * @param fallbackModel requestedModel 为空时的次选（重新生成时是被删回答的模型），再空才用 llm.model
     */
    private LlmCallOptions buildOptions(Boolean enableThinking, String requestedModel,
                                        String fallbackModel, Integer thinkingBudget) {
        Boolean effectiveThinking = (enableThinking != null) ? enableThinking : llmProperties.enableThinking();
        String model = resolveModel(requestedModel, fallbackModel);
        Integer budget = null;
        if (Boolean.TRUE.equals(effectiveThinking) && thinkingBudget != null) {
            if (thinkingBudget <= 0 || thinkingBudget > MAX_THINKING_BUDGET) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "thinkingBudget 必须是 1~" + MAX_THINKING_BUDGET + " 之间的整数");
            }
            budget = thinkingBudget;
        }
        return new LlmCallOptions(model, enableThinking, budget);
    }

    /**
     * 模型白名单校验：请求没带就用 fallbackModel，再没就用服务端 llm.model；
     * 带了但不在白名单里就 400，并把可选清单写进错误文案——界面选错、手填错都能一眼看出该填什么。
     */
    private String resolveModel(String requestedModel, String fallbackModel) {
        String wanted = (requestedModel != null && !requestedModel.isBlank()) ? requestedModel.strip() : fallbackModel;
        if (wanted == null || wanted.isBlank()) {
            return llmProperties.model();
        }
        if (!llmProperties.availableModels().contains(wanted)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的模型: " + wanted + "，可选: " + String.join(" / ", llmProperties.availableModels()));
        }
        return wanted;
    }

    /**
     * SSE 骨架：chat 和 regenerate 共用。取消语义全在这里——
     * 前端 abort、SSE 超时、客户端断网都只是把 cancelled 置真，生成任务在下一个增量自停。
     */
    private SseEmitter startStream(Conversation conversation, List<LlmMessage> history, LlmCallOptions options) {
        Long conversationId = conversation.getId();
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onTimeout(() -> {
            cancelled.set(true);
            log.debug("SSE 超时，停止生成 conversationId={}", conversationId);
        });
        emitter.onError(e -> cancelled.set(true));
        emitter.onCompletion(() -> cancelled.set(true));

        executor.submit(() -> stream(conversation, emitter, cancelled, history, options));
        return emitter;
    }

    private void stream(Conversation conversation, SseEmitter emitter,
                        AtomicBoolean cancelled, List<LlmMessage> history,
                        LlmCallOptions options) {
        StringBuilder full = new StringBuilder();
        // 思考全文单独攒一份用于入库；SurrogateBuffer 只服务于 SSE 增量切分，两者互不影响。
        StringBuilder reasoningFull = new StringBuilder();
        SurrogateBuffer contentBuffer = new SurrogateBuffer();
        SurrogateBuffer reasoningBuffer = new SurrogateBuffer();
        // 计数用于排查「思考远超回答」的成本异常（实测有过一轮思考 2.4 万字、正式回答 1 个字）
        AtomicLong reasoningChars = new AtomicLong();
        // 用量：prompt / completion / reasoning。回调和读取都在本虚拟线程里，普通数组就够。
        // 拿不到就保持 null——不填 0 冒充真实值，界面对 null 显示「—」。
        final Integer[] usage = new Integer[3];
        try {
            llmClient.streamChat(history, options, new LlmStreamListener() {
                @Override
                public void onReasoning(String text) {
                    checkCancelled(cancelled);
                    reasoningChars.addAndGet(text.length());
                    reasoningFull.append(text);
                    String chunk = reasoningBuffer.feed(text);
                    if (!chunk.isEmpty()) {
                        send(emitter, ChatEvent.reasoning(chunk));
                    }
                }

                @Override
                public void onUsage(int promptTokens, int completionTokens, Integer reasoningTokens) {
                    usage[0] = promptTokens;
                    usage[1] = completionTokens;
                    usage[2] = reasoningTokens;
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
            Message saved = saveAssistant(conversation, full.toString(), reasoningFull.toString(), options, usage);
            send(emitter, ChatEvent.done(saved));
            emitter.complete();
            if (reasoningChars.get() > 0) {
                log.debug("思考过程共 {} 字，已推送前端并随助手消息入库 conversationId={}",
                        reasoningChars.get(), conversation.getId());
            }
        } catch (StreamAbortedException e) {
            // 用户停止 / 页面关闭 / 超时：保留已生成内容，不再往前端写任何东西
            savePartial(conversation, full, reasoningFull, options, usage, "生成被中断");
            completeQuietly(emitter);
        } catch (Exception e) {
            log.warn("对话生成失败 conversationId={}", conversation.getId(), e);
            savePartial(conversation, full, reasoningFull, options, usage, "生成失败");
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

    private void savePartial(Conversation conversation, StringBuilder full, StringBuilder reasoning,
                             LlmCallOptions options, Integer[] usage, String reason) {
        if (full.isEmpty()) {
            // 思考阶段就被中断：一条没有正文的助手消息没有意义，思考也不单独留
            return;
        }
        try {
            saveAssistant(conversation, full.toString(), reasoning.toString(), options, usage);
            log.debug("{}，已保存部分回复 conversationId={}", reason, conversation.getId());
        } catch (Exception e) {
            log.warn("{}，保存部分回复失败 conversationId={}", reason, conversation.getId(), e);
        }
    }

    private Message saveMessage(Conversation conversation, Role role, String content, String reasoning) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setReasoning((reasoning == null || reasoning.isBlank()) ? null : reasoning);
        message = messageRepository.save(message);

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        return message;
    }

    /** 助手消息专用：额外记下用了哪个模型、烧了多少 token，事后能对账成本。 */
    private Message saveAssistant(Conversation conversation, String content, String reasoning,
                                  LlmCallOptions options, Integer[] usage) {
        Message message = saveMessage(conversation, Role.ASSISTANT, content, reasoning);
        message.setModel(options.model());
        message.setPromptTokens(usage[0]);
        message.setCompletionTokens(usage[1]);
        message.setReasoningTokens(usage[2]);
        return messageRepository.save(message);
    }

    /**
     * 只取最近 N 条历史，避免长会话把上下文窗口和 token 一起撑爆。
     * <p>
     * LIMIT 下推到 SQL（PageRequest + 按 id 倒序）。以前是 findAll 全量查出来再 subList：
     * 一个聊了 500 轮的会话，每发一句话都要把 500 条 LONGTEXT 拉进内存，再丢掉其中 480 条。
     * <p>
     * {@code limit <= 0} 仍然全量查——配置里写明了「<=0 表示不限制」，不能悄悄换成默认值。
     */
    private List<LlmMessage> recentHistory(Long conversationId) {
        int limit = llmProperties.maxHistoryMessages();
        List<Message> window;
        if (limit > 0) {
            // 按 id 倒序取 limit 条就是最近的 limit 条，再反转成时间正序（模型要求历史从旧到新）
            window = new ArrayList<>(messageRepository.findByConversationId(
                    conversationId, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id"))));
            Collections.reverse(window);
        } else {
            window = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        }
        // 助手消息把存库的思考一起回传：qwen3.8 系 preserve_thinking 默认 true，
        // 缺了 reasoning_content 虽不报错，但多轮推理质量会打折（官方 Chat 文档）
        return window.stream()
                .map(m -> new LlmMessage(m.getRole().name().toLowerCase(), m.getContent(), m.getReasoning()))
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
