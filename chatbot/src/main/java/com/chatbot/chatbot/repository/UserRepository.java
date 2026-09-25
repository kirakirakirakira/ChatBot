package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 登录用：按用户名精确查（username 上有唯一索引）。 */
    Optional<User> findByUsername(String username);

    /** 管理员看的用户列表，按 id 升序：初始 admin 排第一。 */
    List<User> findAllByOrderByIdAsc();

    /**
     * 登录成功时记一笔最近登录时间。
     * 用 @Modifying + JPQL 而不是 load 出来再 save()：登录是热路径，整行 update 会顺带把
     * password_changed_at 等无关列也写一遍；单列 update 语义最小。调用方必须自带事务。
     */
    @Modifying
    @Query("update User u set u.lastLoginAt = :now where u.id = :id")
    void touchLastLogin(@Param("id") Long id, @Param("now") LocalDateTime now);
}