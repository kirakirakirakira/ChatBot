package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 编辑一条记忆；内容或类型都可改，改完来源标记为「手动」。 */
public record UpdateMemoryRequest(
        @NotBlank(message = "记忆类型不能为空") String kind,
        @NotBlank(message = "记忆内容不能为空")
        @Size(max = 500, message = "记忆内容不能超过 500 字") String content) {
}
