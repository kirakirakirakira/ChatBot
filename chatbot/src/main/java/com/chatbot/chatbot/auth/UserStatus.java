package com.chatbot.chatbot.auth;

/**
 * 账号状态。sys_user.status 存 int 而不是 boolean / ENUM：以后加「锁定 / 待激活」不用改列类型，
 * 和 role 的处理方式保持一致（见 {@link Roles}）。
 */
public final class UserStatus {

    /** 正常：可以登录、可以调接口。 */
    public static final int ENABLED = 0;

    /** 禁用：登录直接 403；已登录的账号下一个请求就被 AuthInterceptor 拦成 401。 */
    public static final int DISABLED = 1;

    private UserStatus() {
    }

    /** null 当启用：列是 NOT NULL DEFAULT 0，null 只可能出现在还没落库的内存对象上，防御性兜底。 */
    public static boolean isEnabled(Integer status) {
        return status == null || status == ENABLED;
    }

    /** 中文状态名由后端给出，前端不维护 0/1 映射（和 Roles.label 同一个口径）。 */
    public static String label(Integer status) {
        return isEnabled(status) ? "启用" : "禁用";
    }
}
