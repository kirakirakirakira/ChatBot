package com.chatbot.chatbot.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本地 Mock 实现：不需要任何 API key，用于先跑通整条流式链路。
 * 配置了 llm.api-key 后，系统自动切换为真实的 OpenAI 兼容客户端。
 */
public class MockLlmClient implements LlmClient {

    @Override
    public void streamChat(List<LlmMessage> messages, Consumer<String> onToken) {
        String lastUser = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                lastUser = messages.get(i).content();
                break;
            }
        }

        String reply = "【Mock 回复】收到你的消息：「" + lastUser + "」。\n"
                + "这是本地 Mock 的流式回复。\n"
                + "在 application.properties 里填写 llm.api-key（百炼 API Key）后，就会切换到真实模型。";

        for (char c : reply.toCharArray()) {
            onToken.accept(String.valueOf(c));
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Mock 生成被中断", e);
            }
        }
    }
}
