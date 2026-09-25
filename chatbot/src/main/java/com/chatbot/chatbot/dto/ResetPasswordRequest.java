package com.chatbot.chatbot.dto;

/**
 * 管理员重置别人的密码，两种模式二选一：
 * generate=true 由后端用 SecureRandom 生成 16 位（字母+数字）并只在这一次响应里回显；
 * 否则 newPassword 必填且 6~64。
 * <p>
 * 刻意不加 bean validation：newPassword 是否必填取决于 generate，注解表达不了这种条件约束，
 * 硬加 @NotBlank 会把「随机生成」这条路直接堵死。校验放 AdminUserService，文案同样是中文 400。
 * <p>
 * 成功后 mustChangePassword 置 true、passwordChangedAt 推到当前时间：目标用户手里所有旧 token 立刻失效，
 * 且他下次登录必须自己改一次密码（和本人改密码走的是同一套失效机制，见 AuthInterceptor）。
 */
public record ResetPasswordRequest(String newPassword, Boolean generate) {
}
