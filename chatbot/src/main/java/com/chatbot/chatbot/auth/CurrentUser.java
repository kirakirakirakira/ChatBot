package com.chatbot.chatbot.auth;

/**
 * 当前请求的登录用户：AuthInterceptor 校验通过后放进 request attribute，
 * 控制器声明一个 CurrentUser 形参即可拿到（见 CurrentUserArgumentResolver）。
 * 不用 ThreadLocal：/chat 是 SSE 异步接口，请求线程和生成线程不是同一个。
 */
/**
 * @param systemPrompt 该用户的系统提示词；ChatService 拿它拼 system 消息，
 *                     免得为了一个人设再查一次库（AuthInterceptor 本来就回表查了用户）。
 */
public record CurrentUser(Long id, String username, Integer role, String systemPrompt) {

    public boolean isAdmin() {
        return Roles.isAdmin(role);
    }
}