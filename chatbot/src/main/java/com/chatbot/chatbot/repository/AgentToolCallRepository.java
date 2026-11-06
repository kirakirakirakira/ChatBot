package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.AgentToolCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {

    /** 删会话时清理工具日志；调用方需自带事务。 */
    @Modifying
    @Query("delete from AgentToolCall a where a.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    /** 删号时清理工具日志；调用方需自带事务。 */
    @Modifying
    @Query("delete from AgentToolCall a where a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
