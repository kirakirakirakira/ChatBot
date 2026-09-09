package com.chatbot.chatbot.auth;

/**
 * 用户角色。sys_user.role 存 int 而非字符串/ENUM，以后加角色不用改列类型。
 */
public final class Roles {

    /** 普通用户：聊天相关功能全部可用。 */
    public static final int USER = 0;

    /** 管理员：额外可访问打了 @RequireAdmin 的接口。 */
    public static final int ADMIN = 1;

    private Roles() {
    }

    public static boolean isAdmin(Integer role) {
        return role != null && role == ADMIN;
    }

    /** 中文角色名由后端给出，前端不用再维护 0/1 的映射。 */
    public static String label(Integer role) {
        return isAdmin(role) ? "管理员" : "普通用户";
    }
}