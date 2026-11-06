package com.chatbot.chatbot.service;

import com.chatbot.chatbot.dto.ChatEvent;

/** AgentService 只负责“产生事件”，具体怎么推 SSE / 怎么判断取消由 ChatService 实现。 */
public interface AgentObserver {

    void onEvent(ChatEvent event);

    void checkCancelled();
}
