package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * 取出属于某会话、且还没被任何消息占用的附件 id。
     * 只查 id 不查实体：这一步是校验，把 LONGBLOB 拉进内存是白费的（真正用字节的是拼历史那一步）。
     */
    @Query("select a.id from Attachment a where a.id in :ids and a.conversation.id = :conversationId and a.messageId is null")
    List<Long> findLinkableIds(@Param("ids") Collection<Long> ids, @Param("conversationId") Long conversationId);

    /** 某几条消息各自带的图片，拼多模态历史用。带 blob，调用方应只传「真要发给模型」的那几条消息 id。 */
    List<Attachment> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

    /**
     * 只要元信息不要字节：消息分页列表用。JPQL 构造器投影成 AttachmentVO，避免整表 LONGBLOB 进内存。
     * 第 0 列是 message_id（VO 里没有这个字段，但分组要用），第 1 列是投影结果。
     */
    @Query("select a.messageId, new com.chatbot.chatbot.dto.AttachmentVO(a.id, a.mime, a.fileName, a.sizeBytes) "
            + "from Attachment a where a.messageId in :messageIds order by a.id asc")
    List<Object[]> findMetaByMessageIdIn(@Param("messageIds") Collection<Long> messageIds);

    /** 带归属校验地取一个附件（读字节用）。不存在或不是自己的都返回 empty，调用方统一 404。 */
    @Query("select a from Attachment a where a.id = :id and a.conversation.owner.id = :ownerId")
    Optional<Attachment> findByIdForOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /** 把附件挂到刚存好的用户消息上。带 messageId is null 条件：一个附件只能属于一条消息。 */
    @Modifying
    @Query("update Attachment a set a.messageId = :messageId where a.id in :ids "
            + "and a.conversation.id = :conversationId and a.messageId is null")
    int linkToMessage(@Param("ids") Collection<Long> ids,
                      @Param("messageId") Long messageId,
                      @Param("conversationId") Long conversationId);

    /** 删会话时连带删附件。同 MessageRepository.deleteByConversationId 的理由：派生删除会多跑 N 条 SQL。 */
    @Modifying
    @Query("delete from Attachment a where a.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);
}
