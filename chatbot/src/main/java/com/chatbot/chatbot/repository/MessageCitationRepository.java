package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.MessageCitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, Long> {

    /** 一页消息的引用一次查完，按 id 升序（同一轮工具调用内保持原始顺序）；避免逐条消息 N+1。 */
    List<MessageCitation> findByMessageIdInOrderByIdAsc(List<Long> messageIds);

    /** 删会话时连带删引用。没有外键，必须由调用方显式清。 */
    @Modifying
    @Query("delete from MessageCitation c where c.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    /** 删用户时按「会话归属」清理，理由同 MessageRepository.deleteByOwnerId 的子查询写法。 */
    @Modifying
    @Query("delete from MessageCitation c where c.conversationId in "
            + "(select cv.id from Conversation cv where cv.owner.id = :ownerId)")
    void deleteByOwnerId(@Param("ownerId") Long ownerId);
}
