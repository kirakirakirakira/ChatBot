package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.Size;

/**
 * 修改自己的系统提示词。
 *
 * @param systemPrompt 全空白或 null 表示清除；2000 字上限是界面 textarea 和数据库 TEXT 之间的约定值，
 *                     超了在这里 400，别等前端截断造成「存进去的和看到的不一样」。
 */
public record UpdateSystemPromptRequest(
        @Size(max = 2000, message = "systemPrompt 最长 2000 字") String systemPrompt) {
}
