package com.chatbot.chatbot.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本地 Mock 实现：不需要任何 API key，用于先跑通整条流式链路。
 * 配置了 llm.api-key 后，系统自动切换为真实的 OpenAI 兼容客户端。
 * <p>
 * 思考开关（请求体 enableThinking，未传时用 llm.enable-thinking 配置）为 true 时会先推一小段假思考，
 * 这样没有 API Key 也能验证 reasoning 事件这条链路，顺便看前端「思考中…」的样子。
 */
public class MockLlmClient implements LlmClient {

    private static final long CHAR_DELAY_MS = 15;

    private final LlmProperties props;

    public MockLlmClient(LlmProperties props) {
        this.props = props;
    }

    @Override
    public void streamChat(List<LlmMessage> messages, Boolean enableThinking, LlmStreamListener listener) {
        String lastUser = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                lastUser = messages.get(i).content();
                break;
            }
        }

        // 请求体里的 enableThinking 优先（每条消息可单独开关），没传则回落到 llm.enable-thinking 配置
        Boolean thinking = (enableThinking != null) ? enableThinking : props.enableThinking();
        if (Boolean.TRUE.equals(thinking)) {
            emit(listener::onReasoning,
                    "【Mock 思考】先看用户说了什么：「" + lastUser + "」，再决定怎么组织回复。");
        }

        String reply = "【Mock 回复】收到你的消息：「" + lastUser + "」。\n"
                + "这是本地 Mock 的流式回复。\n"
                + "在 application.properties 里填写 llm.api-key（百炼 API Key）后，就会切换到真实模型。";
        emit(listener::onToken, reply);
    }

    /**
     * 按码点切，不能按 char 切：emoji 这类非 BMP 字符在 UTF-16 里占 2 个 char，
     * 按 char 切会把代理对拆成两个落单 char，Jackson 编不出 UTF-8，SSE 里就变成两个 '?'。
     */
    private static void emit(Consumer<String> sink, String text) {
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int end = index + Character.charCount(codePoint);
            sink.accept(text.substring(index, end));
            index = end;
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(CHAR_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mock 生成被中断", e);
        }
    }
}
