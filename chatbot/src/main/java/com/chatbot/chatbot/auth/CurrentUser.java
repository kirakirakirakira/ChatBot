package com.chatbot.chatbot.auth;

/**
 * 当前请求的登录用户。AuthInterceptor 校验 token 通过后放进 request attribute，
 * 控制器方法直接声明一个 CurrentUser 形参就能拿到（见 CurrentUserArgumentResolver）。
 * <p>
 * 刻意不用 ThreadLocal：/chat 是 SSE 异步接口，请求线程和生成线程不是同一个，
 * ThreadLocal 传不过去还容易漏清理；而且异步请求的 afterCompletion 时机也不好保证。
 */
public record CurrentUser(Long id, String username, Integer role) {

    public boolean isAdmin() {
        return Roles.isAdmin(role);
    }
}