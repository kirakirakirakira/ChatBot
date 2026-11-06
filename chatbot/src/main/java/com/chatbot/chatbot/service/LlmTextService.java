package com.chatbot.chatbot.service;

import com.chatbot.chatbot.llm.LlmClient;
import com.chatbot.chatbot.llm.LlmMessage;
import com.chatbot.chatbot.llm.LlmCallOptions;
import com.chatbot.chatbot.llm.LlmStreamListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 把流式接口收成一次性字符串，供记忆抽取 / 会话摘要这类「后台小调用」使用。
 * <p>
 * 不新开 LlmClient 方法：Mock 与 OpenAI 兼容实现都只实现 streamChat，
 * 这里加一层收集器就能复用，不用同时改两个实现类。
 */
@Service
public class LlmTextService {

    /** 后台调用不需要长文；超过这个长度说明模型跑偏了，直接中断，避免把内存打满。 */
    private static final int MAX_OUTPUT_CHARS = 20_000;

    private final LlmClient llmClient;

    public LlmTextService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String complete(List<LlmMessage> messages, LlmCallOptions options) {
        StringBuilder out = new StringBuilder();
        llmClient.streamChat(messages, options, new LlmStreamListener() {
            @Override
            public void onToken(String text) {
                if (out.length() + text.length() > MAX_OUTPUT_CHARS) {
                    throw new IllegalStateException("后台模型输出超过 " + MAX_OUTPUT_CHARS + " 字符");
                }
                out.append(text);
            }
        });
        return out.toString();
    }
}
