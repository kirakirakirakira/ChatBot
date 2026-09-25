package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
