package com.chatbot.chatbot.llm;

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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容接口客户端：百炼（DashScope）/ DeepSeek / OpenAI 均可直连。
 * 协议：POST {baseUrl}/chat/completions，stream=true，返回 SSE，
 * 每行形如 data: {"choices":[{"delta":{"content":"..."}}]}，最后以 data: [DONE] 结束。
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
    public void streamChat(List<LlmMessage> messages, Consumer<String> onToken) {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "stream", true,
                "messages", messages
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMinutes(5))
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
                    if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                        Delta delta = chunk.choices().get(0).delta();
                        if (delta != null && delta.content() != null && !delta.content().isEmpty()) {
                            onToken.accept(delta.content());
                        }
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

    record Delta(String content) {
    }
}
