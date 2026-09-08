package com.chatbot.chatbot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.properties 中 auth.* 配置。
 *
 * @param tokenSecret          登录 token 的 HMAC-SHA256 签名密钥。泄露 = 任何人都能伪造登录态，
 *                             生产环境必须用环境变量 AUTH_TOKEN_SECRET 覆盖默认值。
 * @param tokenTtlHours        登录态有效期（小时）。过期后接口返回 401，前端自动弹回登录页。
 * @param defaultAdminUsername 首次启动兜底创建的管理员用户名，仅当 sys_user 表为空时生效。
 * @param defaultAdminPassword 首次启动兜底创建的管理员明文密码，入库前做 BCrypt。
 *                             与 sql/init.sql 里那条 INSERT IGNORE 等价，两条路径留一条即可。
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        String tokenSecret,
        @DefaultValue("12") int tokenTtlHours,
        @DefaultValue("admin") String defaultAdminUsername,
        @DefaultValue("admin") String defaultAdminPassword) {
}