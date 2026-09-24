package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 会话列表：只查某个用户的，按最近更新时间倒序。
     * 走 idx_conversation_owner_updated（owner_id + updated_at 复合索引），排序不用回表。
     */
    List<Conversation> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    /**
     * 按 id + 归属查单个会话，业务代码取会话只准用这个。
     * 继承来的 findById 不带归属条件，用了就是越权，评审时看到请直接打回。
     */
    Optional<Conversation> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * 只改标题。刻意用 @Modifying + JPQL 而不是 save()：
     * save() 会触发 @PreUpdate 刷新 updated_at，会话就跳到列表最前面——
     * 改名不是「活动」，不该改变排序。where 里带上 owner_id，越权改名影响行数为 0。
     *
     * @return 影响行数，0 表示会话不存在或不属于该用户
     */
    @Modifying
    @Query("update Conversation c set c.title = :title where c.id = :id and c.owner.id = :ownerId")
    int updateTitle(@Param("id") Long id, @Param("ownerId") Long ownerId, @Param("title") String title);
}
