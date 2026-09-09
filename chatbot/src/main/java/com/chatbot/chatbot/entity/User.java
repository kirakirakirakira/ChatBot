package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 登录用户。表名用 sys_user 而不是 user：MySQL 里 user 既是关键字又是函数名，处处要加反引号。
 * 这个类是表结构的唯一事实源，改了请同步 chatbot/sql/init.sql。
 */
@Entity
@Table(name = "sys_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_sys_user_username", columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，唯一。业务上按「忽略首尾空格」后的值存取。 */
    @Column(nullable = false, length = 50)
    private String username;

    /**
     * BCrypt 哈希，固定 60 字符（列长留到 100 是给以后换算法留余量）。
     * 任何时候都不存明文，也不要塞进 VO 返回给前端。
     */
    @Column(nullable = false, length = 100)
    private String password;

    /** 角色，取值见 auth.Roles：0=普通用户，1=管理员。 */
    @Column(nullable = false)
    private Integer role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 最近一次改密码的时间，初始管理员没改过时为 null。
     * 签发时间（token 的 iat）早于它的登录态一律作废，否则别人手里的旧 token 还能继续用。
     */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }
}