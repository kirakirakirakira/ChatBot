package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.UserMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    /** 某用户的启用记忆，按最近更新时间倒序；条数由调用方传入（注入上限 / 管理列表上限）。 */
    @Query("select m from UserMemory m where m.user.id = :userId and m.status = 0 order by m.updatedAt desc")
    List<UserMemory> findActive(@Param("userId") Long userId, Pageable pageable);

    /** 单条记忆必须同时带 user_id，别人的 id 查不到，统一 404。 */
    Optional<UserMemory> findByIdAndUserId(Long id, Long userId);

    /** 精确去重：同一用户下相同规范化内容只保留一行。 */
    Optional<UserMemory> findByUserIdAndContentHash(Long userId, String contentHash);

    long countByUserIdAndStatus(Long userId, Integer status);

    /** 删号级联用；user_memory.user_id 是 RESTRICT，必须排在删 sys_user 之前。 */
    @Modifying
    @Query("delete from UserMemory m where m.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** 记忆被注入 Prompt 后批量记账；一条 UPDATE 代替逐条 save。 */
    @Modifying
    @Query("update UserMemory m set m.lastUsedAt = :now, m.useCount = m.useCount + 1 where m.id in :ids")
    void touchUsed(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}
