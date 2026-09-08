package com.chatbot.chatbot.repository;

import com.chatbot.chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 登录用：按用户名精确查（username 上有唯一索引）。 */
    Optional<User> findByUsername(String username);

    /** 管理员看的用户列表，按 id 升序：初始 admin 永远排第一。 */
    List<User> findAllByOrderByIdAsc();
}