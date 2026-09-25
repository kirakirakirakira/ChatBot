package com.chatbot.chatbot.dto;

/**
 * @param generatedPassword    只在 generate=true 时有值，且只在这一次响应里出现——库里存的是 BCrypt 哈希，
 *                             关掉这个弹窗就再也查不回来了；管理员手填密码时是 null（他自己知道填了什么）
 * @param mustChangePassword   恒为 true，告诉前端「该用户下次登录会被强制改密」，界面可以直接提示管理员转告
 */
public record ResetPasswordResult(String generatedPassword, Boolean mustChangePassword) {
}
