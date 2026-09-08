package com.chatbot.chatbot.auth;

/**
 * 用户角色。数据库 sys_user.role 存数字（int），不存字符串：
 * 以后加角色不用改列类型，也不会像 ENUM 那样每加一个值都要 ALTER TABLE。
 */
public final class Roles {

    /** 普通用户：登录后可以用聊天相关的全部功能。 */
    public static final int USER = 0;

    /** 管理员：普通用户的能力 + 打了 @RequireAdmin 的接口（目前是 GET /api/users）。 */
    public static final int ADMIN = 1;

    private Roles() {
    }

    public static boolean isAdmin(Integer role) {
        return role != null && role == ADMIN;
    }

    /** 中文角色名由后端给出，省得前端再维护一份「0/1 分别叫什么」的映射。 */
    public static String label(Integer role) {
        return isAdmin(role) ? "管理员" : "普通用户";
    }
}