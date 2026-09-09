package com.chatbot.chatbot.auth;

/**
 * 当前请求的登录用户：AuthInterceptor 校验通过后放进 request attribute，
 * 控制器声明一个 CurrentUser 形参即可拿到（见 CurrentUserArgumentResolver）。
 * 不用 ThreadLocal：/chat 是 SSE 异步接口，请求线程和生成线程不是同一个。
 */
public record CurrentUser(Long id, String username, Integer role) {

    public boolean isAdmin() {
        return Roles.isAdmin(role);
    }
}