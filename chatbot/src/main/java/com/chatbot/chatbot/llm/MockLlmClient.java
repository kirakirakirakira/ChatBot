package com.chatbot.chatbot.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * 本地 Mock：不需要 API key，用来先跑通整条流式链路；配了 llm.api-key 就自动切换为真实的 OpenAI 兼容客户端。
 * 思考开关为 true 时先推一小段假思考，没有 key 也能验证 reasoning 这条链路。
 */
public class MockLlmClient implements LlmClient {

    private static final long CHAR_DELAY_MS = 15;

    private final LlmProperties props;

    public MockLlmClient(LlmProperties props) {
        this.props = props;
    }

    @Override
    public void streamChat(List<LlmMessage> messages, LlmCallOptions options, LlmStreamListener listener) {
        String lastUser = "";
        String systemPrompt = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role()) && lastUser.isEmpty()) {
                lastUser = messages.get(i).text();
            }
            if ("system".equals(messages.get(i).role())) {
                systemPrompt = messages.get(i).text();
                break;
            }
        }

        // 选项里的 enableThinking 优先（每条消息可单独开关），没传则回落到 llm.enable-thinking
        Boolean thinking = (options.enableThinking() != null) ? options.enableThinking() : props.enableThinking();
        String reasoningText = "【Mock 思考】先看用户说了什么：「" + lastUser + "」，再决定怎么组织回复。";
        if (Boolean.TRUE.equals(thinking)) {
            emit(listener::onReasoning, reasoningText);
        }

        // 把收到的人设回显一行：没有真实 key 时也能肉眼确认 system prompt 真的进了模型输入
        // 联网开关也回显一行：没 key 时也能确认开关真的传到了调用层
        String searched = Boolean.TRUE.equals(options.enableSearch())
                ? "【Mock 联网】已模拟检索到 3 条网页结果。\n"
                : "";
        // 图片也回显一行：没有真实 key 时，这是唯一能确认「多模态入参真的拼进去了」的地方
        int images = messages.stream().mapToInt(LlmMessage::imageCount).sum();
        String pics = images > 0
                ? "【Mock 图片】收到 " + images + " 张图片（Mock 不解析像素，只确认 image_url 段已送达）。\n"
                : "";
        String persona = (systemPrompt == null || systemPrompt.isBlank())
                ? ""
                : "【Mock 人设】" + systemPrompt.substring(0, Math.min(30, systemPrompt.length())) + "\n";
        String reply = persona
                + searched
                + pics
                + "【Mock 回复】收到你的消息：「" + lastUser + "」。\n"
                + "这是本地 Mock 的流式回复。\n"
                + "在 application.properties 里填写 llm.api-key（百炼 API Key）后，就会切换到真实模型。";
        emit(listener::onToken, reply);

        // 假用量：按 4 字符 ≈ 1 token 粗估，让「用量展示」这条链路在没有 key 时也能验
        int prompt = messages.stream().mapToInt(m -> m.text().length() / 4 + 1).sum();
        int completion = reply.length() / 4 + 1;
        Integer reasoning = Boolean.TRUE.equals(thinking) ? reasoningText.length() / 4 + 1 : null;
        listener.onUsage(prompt, completion, reasoning);
    }

    /**
     * 按码点切而不是按 char 切：非 BMP 字符在 UTF-16 里占 2 个 char，
     * 拆成落单 char 后 Jackson 编不出 UTF-8，SSE 里就变成两个 ?。
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
