package com.chatbot.chatbot.entity;

/**
 * 审计动作名。存 varchar 而不是 MySQL ENUM / Java enum：
 * 审计动作会随管理模块增加（今天只有用户管理，以后别的 /api/admin/** 模块也会往这张表写），
 * 用 enum 的话每加一个动作都要手工 ALTER TABLE 改枚举列（`message.role` 就吃过这个亏，见 13.2），
 * 字符串列加动作是零迁移。代价是失去编译期约束，用这个常量类补。
 */
public final class AuditAction {

    public static final String CREATE_USER = "CREATE_USER";
    public static final String UPDATE_ROLE = "UPDATE_ROLE";
    public static final String UPDATE_STATUS = "UPDATE_STATUS";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";
    public static final String REVOKE_SESSIONS = "REVOKE_SESSIONS";
    public static final String DELETE_USER = "DELETE_USER";

    private AuditAction() {
    }

    /**
     * 界面展示的中文名。不认识的动作名**原样返回**而不是报错或返回 null：
     * 审计表里可能出现「新代码还不认识的老动作」（回滚过版本时），界面不该因此整页崩掉。
     */
    public static String label(String action) {
        return switch (action == null ? "" : action) {
            case CREATE_USER -> "创建用户";
            case UPDATE_ROLE -> "修改角色";
            case UPDATE_STATUS -> "启用 / 禁用";
            case RESET_PASSWORD -> "重置密码";
            case REVOKE_SESSIONS -> "强制下线";
            case DELETE_USER -> "删除用户";
            default -> action;
        };
    }
}
