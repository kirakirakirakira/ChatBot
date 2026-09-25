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

    /**
     * 管理端准入：管理员**或超级管理员**（{@code @RequireAdmin} 的判定口径）。
     * 方法名保留 isAdmin 是因为拦截器与前端到处在用；「是不是恰好管理员这一档」用 {@link Roles#isAdmin(Integer)}。
     */
    public boolean isAdmin() {
        return Roles.canAccessAdmin(role);
    }

    public boolean isSuperAdmin() {
        return Roles.isSuperAdmin(role);
    }

    /** 自己的管理层级：service 里做「只能上对下」比较时用。 */
    public int rank() {
        return Roles.rank(role);
    }
}