package com.chatbot.chatbot.auth;

import java.util.List;

/**
 * 用户角色。sys_user.role 存 int 而非字符串/ENUM，以后加角色不用改列类型。
 * <p>
 * 2026-09-25 起角色是**层级（rank）模型**：管理操作只允许「上对下」，
 * 超级管理员 &gt; 管理员 &gt; 普通用户 &gt; 访客。层级用 {@link #rank(Integer)} 表达，
 * 和 code 解耦——code 是落库值（0/1 已存在，不能改含义），rank 是权限顺序。
 */
public final class Roles {

    /** 普通用户：聊天相关功能全部可用。 */
    public static final int USER = 0;

    /** 管理员：可进管理端，但只能管理层级比自己低的用户（普通用户 / 访客）。 */
    public static final int ADMIN = 1;

    /** 超级管理员：管理端全权，可以管理管理员；也管不了自己和本层级的其他超管。 */
    public static final int SUPER_ADMIN = 2;

    /**
     * 访客：应用内权限与普通用户相同（能聊天、能管自己的会话），只是管理层级最低。
     * 存在的意义是「降权但不禁用」的承接位：账号出问题又不至于封号时降到这一档。
     */
    public static final int GUEST = 3;

    private Roles() {
    }

    /** 管理层级，数字越大越高；不认识的角色返回 -1（一律视为最低，宁可管不了也别越权）。 */
    public static int rank(Integer role) {
        if (role == null) {
            return -1;
        }
        return switch (role) {
            case GUEST -> 0;
            case USER -> 1;
            case ADMIN -> 2;
            case SUPER_ADMIN -> 3;
            default -> -1;
        };
    }

    public static boolean isSuperAdmin(Integer role) {
        return role != null && role == SUPER_ADMIN;
    }

    public static boolean isAdmin(Integer role) {
        return role != null && role == ADMIN;
    }

    /**
     * 管理端准入（{@code @RequireAdmin} 的判定口径）：管理员或超级管理员。
     * 普通用户和访客连管理端的门都进不来，层级规则是进门之后再算的。
     */
    public static boolean canAccessAdmin(Integer role) {
        return rank(role) >= rank(ADMIN);
    }

    /** 是否已知角色：建号 / 改角色的入参校验用，挡住 role=99 这种脏值。 */
    public static boolean isKnown(Integer role) {
        return rank(role) >= 0;
    }

    /** 全部已知角色，按层级从高到低：options 接口按它过滤出「当前操作者能指派的角色」。 */
    public static List<Integer> knownByRankDesc() {
        return List.of(SUPER_ADMIN, ADMIN, USER, GUEST);
    }

    /** 中文角色名由后端给出，前端不维护 code 到中文的映射。不认识的角色原样带出 code，便于排查脏数据。 */
    public static String label(Integer role) {
        if (role == null) {
            return "未知角色";
        }
        return switch (role) {
            case USER -> "普通用户";
            case ADMIN -> "管理员";
            case SUPER_ADMIN -> "超级管理员";
            case GUEST -> "访客";
            default -> "未知角色(" + role + ")";
        };
    }
}
