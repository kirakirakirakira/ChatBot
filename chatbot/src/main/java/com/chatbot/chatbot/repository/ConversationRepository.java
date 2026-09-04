package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** 会话列表：按最近更新时间倒序。 */
    List<Conversation> findAllByOrderByUpdatedAtDesc();
}
