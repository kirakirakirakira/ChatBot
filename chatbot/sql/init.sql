-- 首次运行前执行一次：建库 + 建表。
-- 结构与 Hibernate 7（spring.jpa.hibernate.ddl-auto=update）在空库上自动生成的结果、
-- 也就是当前线上 chatbot 库的结构完全一致（MySQL 8.0，utf8mb4 / utf8mb4_unicode_ci）。
-- 表结构的唯一事实源是实体类 com.chatbot.chatbot.entity.Conversation / Message / User，改实体后请同步这里。
-- 脚本可重复执行：全部使用 IF NOT EXISTS；ddl-auto=update 只会补缺失的表和列，不会改动已存在的列。

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
-- password 存 BCrypt 哈希（固定 60 字符），任何情况下都不存明文；
-- role 用数字：0=普通用户，1=管理员，取值定义在 com.chatbot.chatbot.auth.Roles；
-- password_changed_at 为 NULL 表示从没改过密码；签发时间（token 的 iat）早于它的登录态一律作废。
--   这一列必须保持 datetime(6)：后端按「毫秒」比较 iat 和它，精度掉到秒的话，
--   改密码那一秒内签发的旧 token 会躲过失效判断。
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
-- 用 INSERT IGNORE + username 唯一索引保证脚本可重复执行：已经有 admin 时既不重复插入，
-- 也不会把人家改过的密码覆盖回 admin。登录后请立刻在「修改密码」里换掉这个默认密码。
INSERT IGNORE INTO `sys_user` (`username`, `password`, `role`, `created_at`)
VALUES ('admin', '$2a$10$lN0TQaxdLsYVpQ9wDx5OB.cucn20byv6DYaSmL32FvvtzDZWlEpmi', 1, NOW(6));

-- 备注：
-- 1) role 的取值集合由 Role 枚举决定（Hibernate 按字母序生成）。以后给 Role 加新值（例如 SYSTEM）时，
--    已存在的库不会自动变更列类型，需要手工执行：
--    ALTER TABLE `message` MODIFY `role` enum('ASSISTANT','SYSTEM','USER') NOT NULL;
-- 2) 会话列表按 updated_at 倒序查询，数据量大后可以自行加索引（Hibernate 不会建）：
--    CREATE INDEX `idx_conversation_updated_at` ON `conversation` (`updated_at`);
-- 3) sys_user.role 只是普通 int，以后加角色（比如 2=运营）不用改表结构；
--    权限判断集中在 com.chatbot.chatbot.auth.Roles 和 @RequireAdmin 注解上，
--    别在业务代码里到处散着写 role == 1。
-- 4) 不执行本脚本、直接靠 spring.jpa.hibernate.ddl-auto=update 启动也能登录：
--    后端发现 sys_user 是空表时，AdminUserInitializer 会自动建一个 admin（密码见
--    auth.default-admin-password，默认 admin），与上面的 INSERT IGNORE 等价。
