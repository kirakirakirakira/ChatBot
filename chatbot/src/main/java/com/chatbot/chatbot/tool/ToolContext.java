package com.chatbot.chatbot.tool;

import com.chatbot.chatbot.auth.CurrentUser;

/** 工具执行上下文。工具只能以当前登录用户身份操作，不能提权。 */
public record ToolContext(CurrentUser user, Long conversationId, String callId) {
}
