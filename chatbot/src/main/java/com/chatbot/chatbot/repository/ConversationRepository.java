package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Conversation;
import org.springframework.data.domain.Pageable;
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

    /** 同上，但带页大小限制；「列出我的会话」工具只取前 N 条，不需要把全量列表拖回来。 */
    List<Conversation> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);

    /** 按标题找会话（大小写不敏感）；标题是 varchar(100)，LIKE 成本可接受。 */
    List<Conversation> findByOwnerIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long ownerId, String keyword, Pageable pageable);

    /**
     * 标题或摘要命中关键词。摘要可能很长，但只扫当前用户自己的会话，量级可控；
     * 它的价值是「只记得主题、不记得原话」时把会话找出来，真正的消息级命中走 MessageRepository 的全文检索。
     */
    @Query("select c from Conversation c where c.owner.id = :ownerId and c.id <> :excludeId "
            + "and (lower(c.title) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(c.summary, '')) like lower(concat('%', :query, '%'))) "
            + "order by c.updatedAt desc")
    List<Conversation> searchOwnedByKeyword(@Param("ownerId") Long ownerId,
                                            @Param("excludeId") Long excludeId,
                                            @Param("query") String query,
                                            Pageable pageable);

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

    /**
     * 只更新摘要及覆盖游标，不触发 @PreUpdate 刷新 updated_at——摘要是后台行为，
     * 不该把会话顶到列表最前面。调用方需自带事务。
     */
    @Modifying
    @Query("update Conversation c set c.summary = :summary, c.summaryUpToMessageId = :upTo, "
            + "c.summaryUpdatedAt = :updatedAt where c.id = :id")
    int updateSummary(@Param("id") Long id,
                      @Param("summary") String summary,
                      @Param("upTo") Long upTo,
                      @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /**
     * 删用户时连带删他的全部会话：管理员删号不该留下一堆 owner_id 指向不存在用户的孤儿会话。
     * <p>
     * 顺序上它夹在中间——必须排在删附件 / 消息之后（它们是 RESTRICT 的子表），
     * 又必须排在删 sys_user 之前（fk_conversation_owner 也是 RESTRICT，先删用户行会撞 errno 1451）。
     * 完整顺序见 AdminUserService.delete。
     */
    @Modifying
    @Query("delete from Conversation c where c.owner.id = :ownerId")
    void deleteByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 某用户的会话数，给「个人信息」页的使用统计用。
     * 派生查询就够（owner_id 上有复合索引，count 直接走索引），不需要手写 JPQL。
     */
    long countByOwnerId(Long ownerId);
}
