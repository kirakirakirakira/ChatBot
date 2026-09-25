-- 首次运行前执行一次：建库 + 建表（MySQL 8.0，utf8mb4 / utf8mb4_unicode_ci）。
-- 结构与 Hibernate ddl-auto=update 自动生成的结果一致；表结构的唯一事实源是实体类 entity.Conversation / Message / User，改实体后请同步这里。
-- 脚本可重复执行：全部用 IF NOT EXISTS；ddl-auto=update 只补缺失的表和列，不会改动已存在的列。
--
-- 建表顺序刻意是 sys_user → conversation → message：conversation.owner_id 外键指向 sys_user，
-- 反过来写会报 errno 150（引用了还不存在的表）。

CREATE DATABASE IF NOT EXISTS chatbot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chatbot;

-- 用户（登录 + 角色），对应实体 com.chatbot.chatbot.entity.User。
-- password 存 BCrypt 哈希（固定 60 字符），任何情况下都不存明文。
-- role 用数字：0=普通用户，1=管理员，取值定义在 com.chatbot.chatbot.auth.Roles；以后加角色不用改表结构。
-- status 用数字：0=启用，1=禁用，取值定义在 com.chatbot.chatbot.auth.UserStatus；禁用登录 403、已登录的下一个请求 401。
-- password_changed_at 为 NULL 表示从没改过密码；签发时间（token 的 iat）早于它的登录态一律作废。
--   这列必须保持 datetime(6)：后端按毫秒比较 iat 和它，精度掉到秒会让改密码那一秒签发的旧 token 躲过失效判断。
-- last_login_at 为 NULL 表示从没登录过；must_change_password=1 表示管理员重置过密码、本人还没改。
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT,
  `username`             varchar(50)  NOT NULL,
  `password`             varchar(100) NOT NULL,
  `role`                 int          NOT NULL,
  `status`               int          NOT NULL DEFAULT 0,
  `created_at`           datetime(6)  NOT NULL,
  `password_changed_at`  datetime(6)  DEFAULT NULL,
  `last_login_at`        datetime(6)  DEFAULT NULL,
  `must_change_password` tinyint(1)   NOT NULL DEFAULT 0,
  `system_prompt`        text         DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 会话（对应左侧会话列表的一项）
-- owner_id NOT NULL：一个会话必须属于某个用户，没有「公共会话」。所有查询都带 owner_id 条件，
--   查不到（不存在 or 是别人的）一律 404，见 ConversationService.requireOwned 的注释。
-- idx_conversation_owner_updated 是给「按用户查列表 + updated_at 倒序」这一条查询准备的复合索引，
--   排序直接在索引里完成，不用回表也不用 filesort。单列的 updated_at 索引不再需要。
CREATE TABLE IF NOT EXISTS `conversation` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `owner_id`   bigint       NOT NULL,
  `title`      varchar(100) NOT NULL,
  `created_at` datetime(6)  NOT NULL,
  `updated_at` datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_conversation_owner_updated` (`owner_id`, `updated_at`),
  CONSTRAINT `fk_conversation_owner` FOREIGN KEY (`owner_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 单条聊天消息（content / reasoning 用 LONGTEXT；role 由 @Enumerated(EnumType.STRING) 映射为 MySQL ENUM）
-- reasoning 可空：没开启思考或非推理模型时为 NULL。前端靠「有没有值」决定要不要渲染思考折叠块，
--   所以空白思考存 NULL 而不是空串。
-- 消息不直接挂 owner_id：归属通过 conversation 传递，多存一份只会多一份不一致的可能。
--   越权校验在会话层做一次就够，message 的查询永远是「先 requireOwned 再按 conversation_id 查」。
-- 分页靠主键 id 当游标（WHERE id < ? ORDER BY id DESC LIMIT ?），不需要额外索引。
CREATE TABLE IF NOT EXISTS `message` (
  `id`              bigint                   NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint                   NOT NULL,
  `role`            enum('ASSISTANT','USER') NOT NULL,
  `content`         longtext                 NOT NULL,
  `reasoning`       longtext                 DEFAULT NULL,
  `model`           varchar(64)              DEFAULT NULL,
  `prompt_tokens`   int                      DEFAULT NULL,
  `completion_tokens` int                    DEFAULT NULL,
  `reasoning_tokens` int                     DEFAULT NULL,
  `created_at`      datetime(6)              NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6yskk3hxw5sklwgi25y6d5u1l` (`conversation_id`),
  CONSTRAINT `FK6yskk3hxw5sklwgi25y6d5u1l` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 图片附件（多模态输入），对应实体 com.chatbot.chatbot.entity.Attachment。
-- data 用 LONGBLOB 直接存字节：单机部署少一个「文件跑哪去了」的运维面，代价是库变大、备份变慢；
--   图片限 5MB、每条消息限 4 张，量级可控。要多实例部署就把 data 换成对象存储的 key。
-- message_id 可空：NULL = 传上来了还没随消息发出去（孤儿附件，随会话删除一起清掉）。
--   刻意不做外键指向 message：附件先于消息存在（先上传拿 id，再发消息），且一个附件只属于一条消息，
--   这个不变式由 AttachmentRepository.linkToMessage 的 "message_id IS NULL" 条件保证。
-- conversation_id 是外键且 RESTRICT：删会话前必须先删附件，ConversationService.delete 已经按这个顺序做了。
-- size 列叫 size_bytes：JPQL 里 size 是保留函数名。
CREATE TABLE IF NOT EXISTS `attachment` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint       NOT NULL,
  `message_id`      bigint       DEFAULT NULL,
  `mime`            varchar(64)  NOT NULL,
  `file_name`       varchar(255) NOT NULL,
  `size_bytes`      bigint       NOT NULL,
  `data`            longblob     NOT NULL,
  `created_at`      datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_attachment_conversation` (`conversation_id`),
  KEY `idx_attachment_message` (`message_id`),
  CONSTRAINT `fk_attachment_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始管理员：admin / admin（下面是字符串 admin 的 BCrypt 哈希，cost=10）。
-- INSERT IGNORE + username 唯一索引保证脚本可重复执行，也不会把改过的密码覆盖回 admin。登录后请立刻换掉这个默认密码。
INSERT IGNORE INTO `sys_user` (`username`, `password`, `role`, `created_at`)
VALUES ('admin', '$2a$10$lN0TQaxdLsYVpQ9wDx5OB.cucn20byv6DYaSmL32FvvtzDZWlEpmi', 1, NOW(6));

-- ===== 已有库升级：2026-09-24「会话按用户隔离」 =====
-- 全新库不用看这段，上面的 CREATE TABLE 已经是新结构。
-- 旧库必须手工执行下面两步，而且**要在启动新版后端之前做完**：
-- ddl-auto=update 只会补 owner_id 这一列，不会给已有行填值，留下的旧会话归属不明、任何人都查不到。
--
--   -- 1) 旧会话一律清掉（message 外键指向 conversation，必须先删消息）
--   DELETE FROM `message`;
--   DELETE FROM `conversation`;
--
--   -- 2) 补归属列 + 外键 + 复合索引（做完这步再启动后端，Hibernate 就没什么可改的了）
--   ALTER TABLE `conversation`
--     ADD COLUMN `owner_id` bigint NOT NULL AFTER `id`,
--     ADD CONSTRAINT `fk_conversation_owner` FOREIGN KEY (`owner_id`) REFERENCES `sys_user` (`id`),
--     ADD INDEX `idx_conversation_owner_updated` (`owner_id`, `updated_at`);
--
-- 想保住旧会话就别做第 1) 步，改成：先加**可空**列 → UPDATE 把每一行指给某个用户 → 再 MODIFY 成 NOT NULL → 最后加外键。
--
-- ===== 已有库升级：2026-09-24「思考过程入库」 =====
-- 这一次是**可空列**，旧消息保持 NULL 即可，不需要清数据：
--   ALTER TABLE `message` ADD COLUMN `reasoning` longtext NULL AFTER `content`;
--
-- ===== 已有库升级：2026-09-24「用量统计 + 模型记录」 =====
-- 四个可空列，旧消息保持 NULL（不填 0 冒充真实值），不需要清数据：
-- ===== 已有库升级：2026-09-24「每用户系统提示词」 =====
-- 可空列，没设过的用户保持 NULL（= 不下发 system 消息），不需要清数据：
--   ALTER TABLE `sys_user` ADD COLUMN `system_prompt` text NULL AFTER `password_changed_at`;
--
--   ALTER TABLE `message`
--     ADD COLUMN `model` varchar(64) DEFAULT NULL AFTER `reasoning`,
--     ADD COLUMN `prompt_tokens` int DEFAULT NULL AFTER `model`,
--     ADD COLUMN `completion_tokens` int DEFAULT NULL AFTER `prompt_tokens`,
--     ADD COLUMN `reasoning_tokens` int DEFAULT NULL AFTER `completion_tokens`;
--
-- ===== 已有库升级：2026-09-25「账号状态 / 最近登录 / 强制改密标记」 =====
-- status 与 must_change_password 是**带 DEFAULT 的 NOT NULL 列**：MySQL 会给已有行填默认值 0，
--   即「老账号一律视为启用、不强制改密」，不会把任何人锁在门外，也不需要清数据。
--   （不写 DEFAULT 的话，严格模式下给有数据的表加 NOT NULL 列会直接失败，这是刻意写的。）
-- last_login_at 可空，没登录过的保持 NULL，不填假时间冒充真实值。
--   ALTER TABLE `sys_user`
--     ADD COLUMN `status`               int         NOT NULL DEFAULT 0 AFTER `role`,
--     ADD COLUMN `last_login_at`        datetime(6) DEFAULT NULL AFTER `password_changed_at`,
--     ADD COLUMN `must_change_password` tinyint(1)  NOT NULL DEFAULT 0 AFTER `last_login_at`;

-- 备注：
-- 1) message.role 的取值集合由 Role 枚举决定（Hibernate 按字母序生成）。以后给 Role 加新值（例如 SYSTEM）时，
--    已存在的库不会自动变更列类型，需要手工执行：
--    ALTER TABLE `message` MODIFY `role` enum('ASSISTANT','SYSTEM','USER') NOT NULL;
-- 2) 删除用户目前不是功能，所以 fk_conversation_owner 用的是默认的 RESTRICT：
--    真要加「删号」功能，得先决定他名下的会话是级联删还是转交，别直接指望数据库报错拦住你。
