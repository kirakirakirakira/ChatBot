package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotNull;

/** 个人设置里的 AI 记忆总开关。 */
public record UpdateMemorySettingsRequest(@NotNull(message = "enabled 不能为空") Boolean enabled) {
}
