package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.chatbot.chatbot.auth.UserStatus;

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

    /**
     * 该用户的系统提示词：每轮对话作为 system 消息放在历史最前面。
     * 挂在用户上而不是会话上：同一个人不管开几个会话，人设保持一致；换账号就是另一套人设。
     * NULL = 没设，后端不下发 system 消息。
     */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /**
     * 账号状态，取值见 auth.UserStatus：0=启用，1=禁用。
     * 初值写在 Java 侧而不是只靠列 DEFAULT：Hibernate 插入时会把 null 显式写进 SQL，
     * 只靠数据库 DEFAULT 兜不住 NOT NULL，所以任何 new User() 的路径都得自带初值。
     */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private Integer status = UserStatus.ENABLED;

    /** 最近一次登录成功的时间；从没登录过为 NULL。管理界面「最近登录」列的数据源。 */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 管理员重置过密码、本人还没改：登录 / /me 响应里带出去，前端据此强制弹改密框。
     * 本人改密成功（PUT /users/me/password）后清回 false。
     */
    @Column(name = "must_change_password", nullable = false, columnDefinition = "tinyint(1) not null default 0")
    private Boolean mustChangePassword = Boolean.FALSE;

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

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }
}