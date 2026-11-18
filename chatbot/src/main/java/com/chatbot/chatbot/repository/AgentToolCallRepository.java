package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.AgentToolCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCall, Long> {

    /** 一页消息的工具步骤一次查完；只回放 status=OK/ERROR 的已完成调用。 */
    List<AgentToolCall> findByMessageIdInOrderByIdAsc(List<Long> messageIds);

    /**
     * 助手消息入库后，把本轮的工具调用挂到这条消息上。
     * call_id 全局唯一，仍带 conversation_id / user_id 是防御性条件：跨会话重放不可能命中。
     */
    @Modifying
    @Query("update AgentToolCall a set a.messageId = :messageId "
            + "where a.conversationId = :conversationId and a.userId = :userId and a.callId in :callIds")
    int attachToMessage(@Param("conversationId") Long conversationId,
                        @Param("userId") Long userId,
                        @Param("callIds") List<String> callIds,
                        @Param("messageId") Long messageId);

    /** 删会话时清理工具日志；调用方需自带事务。 */
    @Modifying
    @Query("delete from AgentToolCall a where a.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    /** 删号时清理工具日志；调用方需自带事务。 */
    @Modifying
    @Query("delete from AgentToolCall a where a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
