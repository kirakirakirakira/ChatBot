package com.chatbot.chatbot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties 中 llm.* 配置。
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(String baseUrl, String apiKey, String model) {
}
