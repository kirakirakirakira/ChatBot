package com.chatbot.chatbot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.properties 中 auth.* 配置。
 * @param tokenSecret          HMAC-SHA256 签名密钥，泄露即可伪造登录态，生产环境必须用环境变量 AUTH_TOKEN_SECRET 覆盖。
 * @param tokenTtlHours        登录态有效期（小时），过期后接口返回 401。
 * @param defaultAdminUsername 首次启动兜底创建的**超级管理员**用户名，仅当 sys_user 为空时创建；
 *                             之后每次启动还会用它做「系统里没有启用的超级管理员就把它提回来」的自愈（见 SeedUserInitializer）。
 * @param defaultAdminPassword 该管理员的明文密码，入库前做 BCrypt；与 sql/init.sql 里的 INSERT IGNORE 二选一。
 * @param seedTestUsers        是否补齐「每个角色一个测试账号」（test_super / test_admin / test_user / test_guest）。
 *                             默认开：本项目是演示 / 教学用途，层级权限（谁能管谁）没有多角色账号根本没法验证。
 *                             **生产环境请用 AUTH_SEED_TEST_USERS=false 关掉**，否则每次启动都会把测试账号补回来。
 * @param testUserPassword     这些测试账号的统一密码（明文，入库前 BCrypt）。默认 test123456。
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String tokenSecret,
        @DefaultValue("12") int tokenTtlHours,
        @DefaultValue("admin") String defaultAdminUsername,
        @DefaultValue("admin") String defaultAdminPassword,
        @DefaultValue("true") boolean seedTestUsers,
        @DefaultValue("test123456") String testUserPassword) {
}
