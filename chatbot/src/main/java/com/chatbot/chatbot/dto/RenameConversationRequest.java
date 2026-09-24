package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话重命名请求体。
 *
 * @param title 新标题。100 是 conversation.title 的列宽，超了在这里就 400，别等数据库报错。
 */
public record RenameConversationRequest(
        @NotBlank(message = "title 不能为空")
        @Size(max = 100, message = "title 最长 100 字") String title) {
}
