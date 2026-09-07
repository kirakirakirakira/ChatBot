package com.chatbot.chatbot.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容接口客户端：百炼（DashScope）/ DeepSeek / OpenAI 均可直连。
 * 协议：POST {baseUrl}/chat/completions，stream=true，返回 SSE，
 * 每行形如 data: {"choices":[{"delta":{"content":"..."}}]}，最后以 data: [DONE] 结束。
 * <p>
 * 推理模型在正式回答之前会先推一大段思考，用的是同一个 delta 里的另一个字段：
 * <pre>
 * {"choices":[{"delta":{"reasoning_content":"We need...","content":""}}]}  思考阶段，实测重复上千帧
 * {"choices":[{"delta":{"reasoning_content":"","content":"您"}}]}          回答阶段
 * </pre>
 * 两个字段都必须转发。只认 content 的话，思考期间一帧都发不出去：
 * 前端白屏等到思考结束（实测约 110 秒），打字机效果消失，
 * 而且浏览器 / Vite 代理 / Nginx 常见的 60 秒空闲超时会直接掐断连接，
 * 那时回答还没开始，一个字都存不下来。
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OpenAiCompatibleLlmClient(LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void streamChat(List<LlmMessage> messages, Boolean enableThinking, LlmStreamListener listener) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", props.model());
        body.put("stream", true);
        body.put("messages", messages);
        // 思考开关：请求体里的 enableThinking 优先（每条消息可单独开关），
        // 没传（null）则回落到 llm.enable-thinking 配置；两者都是 null 时不下发该参数，
        // 免得直连不认识 enable_thinking 的服务商（OpenAI、DeepSeek 等）直接报 400。
        Boolean thinking = (enableThinking != null) ? enableThinking : props.enableThinking();
        if (thinking != null) {
            body.put("enable_thinking", thinking);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.baseUrl() + "/chat/completions"))
                // 整轮生成的总上限，不是空闲超时：JDK HttpClient 到点会直接关掉流式响应体，
                // 表现为 IOException("closed")。原来写死 5 分钟，推理模型思考超过 5 分钟
                // 就会在第 300 秒被掐断，思考了几万字、正式回答 0 字，全部作废还照样计费。
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("LLM API 返回 " + response.statusCode() + ": " + error);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    Chunk chunk = objectMapper.readValue(data, Chunk.class);
                    if (chunk.choices() == null || chunk.choices().isEmpty()) {
                        continue;
                    }
                    Delta delta = chunk.choices().get(0).delta();
                    if (delta == null) {
                        continue;
                    }
                    // 同一帧里两个字段都可能有值：思考在前、回答在后，保持模型给出的顺序。
                    if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
                        listener.onReasoning(delta.reasoningContent());
                    }
                    if (delta.content() != null && !delta.content().isEmpty()) {
                        listener.onToken(delta.content());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 请求被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("调用 LLM API 失败: " + e.getMessage(), e);
        }
    }

    record Chunk(List<Choice> choices) {
    }

    record Choice(Delta delta) {
    }

    /** 推理模型的思考增量在 reasoning_content，正式回答增量在 content。 */
    record Delta(
            @JsonProperty("reasoning_content") String reasoningContent,
            String content) {
    }
}
