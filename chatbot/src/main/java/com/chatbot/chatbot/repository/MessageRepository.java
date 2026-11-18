package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 某个会话的全部历史消息，按发送顺序。
     * 只留给「不限条数」的场景（{@code llm.max-history-messages <= 0}）；
     * 界面加载和正常的历史窗口都走下面的分页方法，别再全量查。
     */
    List<Message> findByConversationIdOrderByIdAsc(Long conversationId);

    /**
     * 分页取消息（最新一页）。配合 {@code PageRequest.of(0, size, Sort.by(DESC, "id"))} 使用，
     * 返回「最新的 size 条、id 倒序」，调用方自己反转成时间正序。
     * <p>
     * 排序刻意放在 Pageable 里而不是方法名上：方法名带 OrderBy 再传带 Sort 的 Pageable，
     * Spring Data 会试图叠加两套排序。
     */
    List<Message> findByConversationId(Long conversationId, Pageable pageable);

    /**
     * 分页取消息（更早一页）：id 严格小于游标的最近 size 条。
     * <p>
     * 用 id 当游标而不是 offset：offset 分页在「一边翻页一边有新消息入库」时会重复或漏消息，
     * 而且翻到第 100 页要扫过前面 99 页的行。id 游标两个问题都没有，
     * 代价是只能一页一页往前翻，不能跳页——聊天界面本来也只需要往前翻。
     */
    List<Message> findByConversationIdAndIdLessThan(Long conversationId, Long beforeId, Pageable pageable);

    /** 取某会话中 id 大于游标的消息，按 id 正序；摘要流水线用。 */
    List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(Long conversationId, Long afterId, Pageable pageable);

    /** 某会话中还没被摘要覆盖的消息数。 */
    long countByConversationIdAndIdGreaterThan(Long conversationId, Long afterId);

    /** 跨会话定位单条消息时用：必须同时带 conversation_id，免得只凭 message id 读到别的会话。 */
    Optional<Message> findByIdAndConversationId(Long id, Long conversationId);

    /**
     * 「第二大脑」主检索：MySQL ngram 全文索引。
     * <p>
     * 只查当前用户自己的消息——owner 条件写在 conversation 上，绝不能省。
     * 排序列和 WHERE 里的 MATCH 必须完全一致，MySQL 才会复用同一个 relevance 值。
     * excludeConversationId 由调用方传 -1 表示不排除（SQL 里 {@code <> NULL} 恒为 NULL，不能直接传 null）。
     */
    @Query(value = """
            select m.id, m.conversation_id, c.title, m.role, m.content, m.created_at
            from message m
            join conversation c on c.id = m.conversation_id
            where c.owner_id = :ownerId
              and m.conversation_id <> :excludeConversationId
              and match(m.content) against (:query in natural language mode)
            order by match(m.content) against (:query in natural language mode) desc, m.id desc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> searchOwnedFullText(@Param("ownerId") Long ownerId,
                                       @Param("excludeConversationId") Long excludeConversationId,
                                       @Param("query") String query,
                                       @Param("limit") int limit);

    /**
     * 全文索引不可用（还没建好 / 分词器不支持）或全文没命中时的兜底。
     * LIKE '%关键词%' 扫的是当前用户的消息，量级可控；不追求相关性排序，按时间倒序即可。
     */
    @Query(value = """
            select m.id, m.conversation_id, c.title, m.role, m.content, m.created_at
            from message m
            join conversation c on c.id = m.conversation_id
            where c.owner_id = :ownerId
              and m.conversation_id <> :excludeConversationId
              and m.content like concat('%', :query, '%')
            order by m.id desc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> searchOwnedLike(@Param("ownerId") Long ownerId,
                                   @Param("excludeConversationId") Long excludeConversationId,
                                   @Param("query") String query,
                                   @Param("limit") int limit);

    /**
     * 删除某会话的全部消息。用 @Modifying + JPQL 而不是派生 deleteBy：
     * 派生删除会先 select 再逐条 delete，长会话删一次就是 2N 条 SQL。调用方需自带事务。
     */
    @Modifying
    @Query("delete from Message m where m.conversation.id = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 删用户时连带删他的全部消息，理由同 deleteByConversationId（content 是 LONGTEXT，逐条 select 不值）。
     * 用子查询而不是多级隐式连接，理由同 AttachmentRepository.deleteByOwnerId。
     * 必须排在删会话之前：fk_message_conversation 是 RESTRICT。
     */
    @Modifying
    @Query("delete from Message m where m.conversation.id in "
            + "(select c.id from Conversation c where c.owner.id = :ownerId)")
    void deleteByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 某用户名下的消息总数（「个人信息」页的使用统计）。
     * message 没有 owner_id 列，归属靠 conversation 传递，所以这里用子查询而不是多级隐式连接
     * ——和 deleteByOwnerId 同一个理由。
     * <p>
     * 这是本项目唯一一处对 LONGTEXT 大表做全量 count 的地方，只在用户主动打开个人页时跑一次，
     * 不在登录 / 聊天这类热路径上（见 UserProfileStatsVO 的注释）。
     */
    @Query("select count(m) from Message m where m.conversation.id in "
            + "(select c.id from Conversation c where c.owner.id = :ownerId)")
    long countByOwnerId(@Param("ownerId") Long ownerId);
}
