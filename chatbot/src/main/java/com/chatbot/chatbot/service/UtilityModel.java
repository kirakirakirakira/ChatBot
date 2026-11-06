package com.chatbot.chatbot.service;

import com.chatbot.chatbot.llm.LlmProperties;

/** 后台调用选模型：配置的模型不在白名单时回落 llm.model；没 API Key（Mock）时返回 null。 */
final class UtilityModel {

    private UtilityModel() {
    }

    static String resolve(LlmProperties properties, String preferred) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return null;
        }
        String candidate = (preferred == null || preferred.isBlank()) ? properties.model() : preferred.strip();
        if (candidate != null && properties.availableModels() != null
                && properties.availableModels().contains(candidate)) {
            return candidate;
        }
        String fallback = properties.model();
        if (fallback != null && properties.availableModels() != null
                && properties.availableModels().contains(fallback)) {
            return fallback;
        }
        return null;
    }
}
