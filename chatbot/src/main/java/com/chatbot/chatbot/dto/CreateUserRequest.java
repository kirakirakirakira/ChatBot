package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员建号。role / status 不传就是「普通用户 + 启用」。
 * <p>
 * role / status 刻意不用 @Min/@Max 卡范围：合法值集合是 Roles / UserStatus 里的常量，
 * 写进注解就成了第二份事实源，以后加角色必然漏改一处。统一在 AdminUserService 里比对常量集合，
 * 未知值 400「未知角色: n」/「未知状态: n」。
 * <p>
 * username 的长度校验看的是请求体原样，trim 在 service 里做：所以「50 个字符 + 首尾空格」会先被
 * {@code @Size} 挡成 400，而不是 trim 后放行。这是注解校验的固有限制，不值得为它把校验搬进 service。
 */
public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 1, max = 50, message = "用户名长度需在 1~50 之间") String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间") String password,
        Integer role,
        Integer status) {
}
