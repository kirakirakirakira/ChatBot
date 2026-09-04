package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 某个会话的历史消息，按发送顺序。 */
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
