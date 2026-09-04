package com.chatbot.chatbot.config;

import com.chatbot.chatbot.llm.LlmClient;
import com.chatbot.chatbot.llm.LlmProperties;
import com.chatbot.chatbot.llm.MockLlmClient;
import com.chatbot.chatbot.llm.OpenAiCompatibleLlmClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    /**
     * 没配 api-key 就用 Mock，方便本地先把链路跑通。
     */
    @Bean
    public LlmClient llmClient(LlmProperties props, ObjectMapper objectMapper) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            return new MockLlmClient();
        }
        return new OpenAiCompatibleLlmClient(props, objectMapper);
    }
}
