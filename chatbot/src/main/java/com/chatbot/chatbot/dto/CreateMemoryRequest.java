package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 手动新增一条记忆。kind 用字符串接，非法值由 service 转成中文 400。 */
public record CreateMemoryRequest(
        @NotBlank(message = "记忆类型不能为空") String kind,
        @NotBlank(message = "记忆内容不能为空")
        @Size(max = 500, message = "记忆内容不能超过 500 字") String content) {
}
