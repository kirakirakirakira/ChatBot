package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 某个会话的历史消息，按发送顺序。 */
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /**
     * 删除某会话的全部消息。用 @Modifying + JPQL 而不是派生 deleteBy：
     * 派生删除会先 select 再逐条 delete，长会话删一次就是 2N 条 SQL。调用方需自带事务。
     */
    @Modifying
    @Query("delete from Message m where m.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);
}