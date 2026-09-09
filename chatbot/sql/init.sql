-- 首次运行前执行一次：建库 + 建表（MySQL 8.0，utf8mb4 / utf8mb4_unicode_ci）。
-- 结构与 Hibernate ddl-auto=update 自动生成的结果一致；表结构的唯一事实源是实体类 entity.Conversation / Message / User，改实体后请同步这里。
-- 脚本可重复执行：全部用 IF NOT EXISTS；ddl-auto=update 只补缺失的表和列，不会改动已存在的列。

CREATE DATABASE IF NOT EXISTS chatbot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chatbot;

-- 会话（对应左侧会话列表的一项）
CREATE TABLE IF NOT EXISTS `conversation` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `title`      varchar(100) NOT NULL,
  `created_at` datetime(6)  NOT NULL,
  `updated_at` datetime(6)  NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 单条聊天消息（content 用 LONGTEXT；role 由 @Enumerated(EnumType.STRING) 映射为 MySQL ENUM）
CREATE TABLE IF NOT EXISTS `message` (
  `id`              bigint                   NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint                   NOT NULL,
  `role`            enum('ASSISTANT','USER') NOT NULL,
  `content`         longtext                 NOT NULL,
  `created_at`      datetime(6)              NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6yskk3hxw5sklwgi25y6d5u1l` (`conversation_id`),
  CONSTRAINT `FK6yskk3hxw5sklwgi25y6d5u1l` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户（登录 + 角色），对应实体 com.chatbot.chatbot.entity.User。
-- password 存 BCrypt 哈希（固定 60 字符），任何情况下都不存明文。
-- role 用数字：0=普通用户，1=管理员，取值定义在 com.chatbot.chatbot.auth.Roles；以后加角色不用改表结构。
-- password_changed_at 为 NULL 表示从没改过密码；签发时间（token 的 iat）早于它的登录态一律作废。
--   这列必须保持 datetime(6)：后端按毫秒比较 iat 和它，精度掉到秒会让改密码那一秒签发的旧 token 躲过失效判断。
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`                  bigint       NOT NULL AUTO_INCREMENT,
  `username`            varchar(50)  NOT NULL,
  `password`            varchar(100) NOT NULL,
  `role`                int          NOT NULL,
  `created_at`          datetime(6)  NOT NULL,
  `password_changed_at` datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始管理员：admin / admin（下面是字符串 admin 的 BCrypt 哈希，cost=10）。
-- INSERT IGNORE + username 唯一索引保证脚本可重复执行，也不会把改过的密码覆盖回 admin。登录后请立刻换掉这个默认密码。
INSERT IGNORE INTO `sys_user` (`username`, `password`, `role`, `created_at`)
VALUES ('admin', '$2a$10$lN0TQaxdLsYVpQ9wDx5OB.cucn20byv6DYaSmL32FvvtzDZWlEpmi', 1, NOW(6));

-- 备注：
-- 1) message.role 的取值集合由 Role 枚举决定（Hibernate 按字母序生成）。以后给 Role 加新值（例如 SYSTEM）时，
--    已存在的库不会自动变更列类型，需要手工执行：
--    ALTER TABLE `message` MODIFY `role` enum('ASSISTANT','SYSTEM','USER') NOT NULL;
-- 2) 会话列表按 updated_at 倒序查询，数据量大后可以自行加索引（Hibernate 不会建）：
--    CREATE INDEX `idx_conversation_updated_at` ON `conversation` (`updated_at`);
