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
-- role 用数字：0=普通用户，1=管理员，2=超级管理员，3=访客，取值定义在 com.chatbot.chatbot.auth.Roles；以后加角色不用改表结构。
--   角色是层级模型（Roles.rank）：超级管理员 > 管理员 > 普通用户 > 访客，管理操作只允许「上对下」。
-- status 用数字：0=启用，1=禁用，取值定义在 com.chatbot.chatbot.auth.UserStatus；禁用登录 403、已登录的下一个请求 401。
-- password_changed_at 为 NULL 表示从没改过密码；签发时间（token 的 iat）早于它的登录态一律作废。
--   这列必须保持 datetime(6)：后端按毫秒比较 iat 和它，精度掉到秒会让改密码那一秒签发的旧 token 躲过失效判断。
-- last_login_at 为 NULL 表示从没登录过；must_change_password=1 表示管理员重置过密码、本人还没改。
-- nickname / email / phone 是「个人信息」页维护的资料，全部可空（NULL = 没填）：
--   nickname 只做展示（界面回退显示 username），不参与登录、不进审计快照；email 的格式校验在应用层（@Email）。
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
  `nickname`             varchar(50)  DEFAULT NULL,
  `email`                varchar(100) DEFAULT NULL,
  `phone`                varchar(30)  DEFAULT NULL,
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

-- 管理端操作审计，对应实体 com.chatbot.chatbot.entity.AdminAuditLog。
-- 只记成功的写操作，且与业务操作同事务：业务回滚则审计一起回滚，不留「失败但有痕」的假记录。
-- actor_id / target_id 刻意不做外键：删号是功能，管理员自己也可能被删，留痕不该反过来挡住删人；
--   目标被删之后靠 actor_name / target_name 的快照认人。
-- action 存字符串而非 ENUM：加动作零迁移（取值与中文名见 entity/AuditAction）。
-- 新表用 IF NOT EXISTS，全新库与旧库都靠这一段，不需要 ALTER。
CREATE TABLE IF NOT EXISTS `admin_audit_log` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `actor_id`    bigint       NOT NULL,
  `actor_name`  varchar(50)  NOT NULL,
  `action`      varchar(32)  NOT NULL,
  `target_id`   bigint       NOT NULL,
  `target_name` varchar(50)  NOT NULL,
  `detail`      varchar(255) DEFAULT NULL,
  `created_at`  datetime(6)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始**超级管理员**：admin / admin（下面是字符串 admin 的 BCrypt 哈希，cost=10），role=2。
-- 必须是超级管理员：层级规则下管理员管不到管理员，种子若只是管理员将永远造不出超级管理员。
-- INSERT IGNORE + username 唯一索引保证脚本可重复执行，也不会把改过的密码覆盖回 admin。登录后请立刻换掉这个默认密码。
INSERT IGNORE INTO `sys_user` (`username`, `password`, `role`, `created_at`)
VALUES ('admin', '$2a$10$lN0TQaxdLsYVpQ9wDx5OB.cucn20byv6DYaSmL32FvvtzDZWlEpmi', 2, NOW(6));

-- 测试账号：每个角色一个，密码统一是 test123456（下面是它的 BCrypt 哈希，cost=10）。
-- 存在的理由是**层级权限没法用单账号验证**：超管能管管理员、管理员管不了管理员、访客谁都不该管得动，
-- 这三条只有四个不同角色的账号同时在库里才点得出来。
-- 与后端 SeedUserInitializer 是同一份清单的二选一兜底（跑脚本 or 直接启动都会得到同样的账号）：
--   都只在「用户名不存在」时插入，不覆盖已有账号、不重置改过的密码。
-- **生产环境别执行这一段**，或启动时用 AUTH_SEED_TEST_USERS=false 关掉后端的自动补齐。
INSERT IGNORE INTO `sys_user` (`username`, `password`, `role`, `status`, `created_at`)
VALUES ('test_super', '$2a$10$5GosfNYckx.pqmr6U/JHauxCRq/mTJ51kO7EgXBA7Zhe.4CcBe6yq', 2, 0, NOW(6)),
       ('test_admin', '$2a$10$5GosfNYckx.pqmr6U/JHauxCRq/mTJ51kO7EgXBA7Zhe.4CcBe6yq', 1, 0, NOW(6)),
       ('test_user',  '$2a$10$5GosfNYckx.pqmr6U/JHauxCRq/mTJ51kO7EgXBA7Zhe.4CcBe6yq', 0, 0, NOW(6)),
       ('test_guest', '$2a$10$5GosfNYckx.pqmr6U/JHauxCRq/mTJ51kO7EgXBA7Zhe.4CcBe6yq', 3, 0, NOW(6));

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

-- ===== 已有库升级：2026-09-25「角色层级（超级管理员 / 访客）」 =====
-- sys_user.role 是 int，加角色**不需要改列**；但老库里没有超级管理员，而「创建 / 提升管理员」只有超级管理员能做，
-- 所以必须手工把初始账号提升一档，否则系统最高只有管理员、将永远造不出超级管理员：
--   UPDATE sys_user SET role = 2 WHERE id = (SELECT t.id FROM (SELECT MIN(id) AS id FROM sys_user) t);
-- （只提升最早那个账号；要提升别人请自行改 WHERE 条件。访客档是新增的可选项，老库不用动。）
--
-- 备注：
-- 1) message.role 的取值集合由 Role 枚举决定（Hibernate 按字母序生成）。以后给 Role 加新值（例如 SYSTEM）时，
--    已存在的库不会自动变更列类型，需要手工执行：
--    ALTER TABLE `message` MODIFY `role` enum('ASSISTANT','SYSTEM','USER') NOT NULL;
-- 2) 删号已经是功能（DELETE /api/admin/users/{id}，级联删会话 / 消息 / 附件），但外键**仍然是 RESTRICT**：
--    级联是应用层按「附件 → 消息 → 会话 → 用户」的顺序显式删的（见 AdminUserService.delete），
--    不是数据库 ON DELETE CASCADE。别把这里的 RESTRICT 当成「还没做删号」的遗留，也别顺手改成 CASCADE：
--    顺序写在代码里才能在删之前抓一份用户名快照进审计表（用户行一没，就再也认不出删的是谁了）。
--
-- ===== 已有库升级：2026-09-25「个人资料（昵称 / 邮箱 / 手机号）」 =====
-- 三个可空列，老账号保持 NULL（= 没填，界面回退显示 username），不需要清数据：
--   ALTER TABLE `sys_user`
--     ADD COLUMN `nickname` varchar(50)  DEFAULT NULL AFTER `system_prompt`,
--     ADD COLUMN `email`    varchar(100) DEFAULT NULL AFTER `nickname`,
--     ADD COLUMN `phone`    varchar(30)  DEFAULT NULL AFTER `email`;
-- （ddl-auto=update 也会自动补这三列，手工执行只是为了让库里少一次启动期的 DDL。）
--
-- ===== 已有库升级：2026-09-25「测试账号 + 种子账号自愈」 =====
-- 不想跑上面那段 INSERT 的话，直接启动后端即可：SeedUserInitializer 会补齐缺失的测试账号，
-- 并在「系统里一个启用的超级管理员都没有」时把种子账号（auth.default-admin-username，默认 admin）提回超级管理员。
