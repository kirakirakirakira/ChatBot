package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 多继承一个 JpaSpecificationExecutor 是给管理端用户列表的动态查询用的（见 UserSpecifications）：
 * JpaRepository 本身不带 Specification 支持，不显式声明就没有 findAll(Specification, Pageable)。
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** 登录用：按用户名精确查（username 上有唯一索引）。 */
    Optional<User> findByUsername(String username);

    /**
     * 登录成功时记一笔最近登录时间。
     * 用 @Modifying + JPQL 而不是 load 出来再 save()：登录是热路径，整行 update 会顺带把
     * password_changed_at 等无关列也写一遍；单列 update 语义最小。调用方必须自带事务。
     */
    @Modifying
    @Query("update User u set u.lastLoginAt = :now where u.id = :id")
    void touchLastLogin(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 「系统至少保留一个启用的管理员」这道闸的计数：管理端要降级 / 禁用 / 删除一个启用的管理员之前先数一次。
     * 只数 (role, status) 两列，不把管理员名单拉进内存，service 里也就不用手写角色判断。
     */
    long countByRoleAndStatus(Integer role, Integer status);
}