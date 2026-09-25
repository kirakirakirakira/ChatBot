package com.chatbot.chatbot.dto;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 管理台批量操作的动作类型。
 * <p>
 * 反序列化刻意**不直接用它接请求体**（{@link BatchUserRequest#action()} 是 String）：
 * Jackson 认不出枚举值时抛的是 HttpMessageNotReadableException，前端只会看到「请求体不是合法的 JSON」，
 * 管理员根本不知道自己传错了 action。走 String + {@link #of(String)} 才能给出
 * 「未知批量操作: xxx（可用：ENABLE / DISABLE / …）」这种能照着改的中文 400，
 * 和 Roles.isKnown / UserStatus 的未知值处理是同一个口径。
 * <p>
 * 每个动作都对应 AdminUserService 里已有的单条方法：批量**不新写业务规则**，
 * 只是把同一套规则在多个 id 上跑一遍（层级、最后一个管理员、不能对自己下手全部照旧生效）。
 */
public enum BatchUserAction {

    /** 批量启用。 */
    ENABLE,

    /** 批量禁用。受「系统至少保留一个启用的管理员」约束，逐条判定。 */
    DISABLE,

    /** 批量改角色，需要额外带 role。 */
    SET_ROLE,

    /** 批量重置密码：generate=true 每人一个随机密码，否则所有人设成同一个 newPassword。 */
    RESET_PASSWORD,

    /** 批量强制下线（只作废登录态，不改密码）。 */
    REVOKE_SESSIONS,

    /** 批量删号，级联删会话 / 消息 / 附件，不可恢复。 */
    DELETE;

    /** 解析动作名，忽略大小写与首尾空格；认不出来返回 null，由调用方转成 400。 */
    public static BatchUserAction of(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.strip();
        return Arrays.stream(values())
                .filter(action -> action.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 全部动作名，拼进错误文案里，省得前端再维护一份「可选值」清单。 */
    public static String availableNames() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(" / "));
    }

    /** 中文名。审计与批量结果里都用它，界面不再自己映射一遍。 */
    public String label() {
        return switch (this) {
            case ENABLE -> "批量启用";
            case DISABLE -> "批量禁用";
            case SET_ROLE -> "批量改角色";
            case RESET_PASSWORD -> "批量重置密码";
            case REVOKE_SESSIONS -> "批量强制下线";
            case DELETE -> "批量删除";
        };
    }
}
