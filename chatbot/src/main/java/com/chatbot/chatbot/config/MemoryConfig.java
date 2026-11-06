package com.chatbot.chatbot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 单独注册记忆配置，避免把 LlmConfig 变成所有配置的收口处。 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfig {
}
