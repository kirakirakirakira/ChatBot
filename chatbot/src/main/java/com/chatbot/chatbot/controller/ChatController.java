package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.ChatRequest;
import com.chatbot.chatbot.dto.RegenerateRequest;
import com.chatbot.chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 发送消息，SSE 流式返回 AI 回复。
     * CurrentUser 必须在进 service 之前就解析好：SSE 是异步接口，生成跑在别的线程上，
     * 那里拿不到 request attribute，也就补不了归属校验。
     */
    @PostMapping(value = "/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable Long id, @Valid @RequestBody ChatRequest request, CurrentUser user) {
        return chatService.chat(id, request, user);
    }

    /**
     * 重新生成最后一条回答：删掉它并用同一条用户消息重跑。SSE 契约与 /chat 完全一致。
     * 请求体可省略（只带 enableThinking 或干脆不带）。
     */
    @PostMapping(value = "/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable Long id,
                                 @RequestBody(required = false) RegenerateRequest request,
                                 CurrentUser user) {
        return chatService.regenerate(id, request, user);
    }
}
