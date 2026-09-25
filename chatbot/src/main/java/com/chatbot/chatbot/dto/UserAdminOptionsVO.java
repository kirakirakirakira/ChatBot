package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理界面里「角色 / 状态」筛选器和下拉框的数据源。
 * <p>
 * 和 {@link LlmOptionsVO} 同一个思路：可选值由后端下发，前端不写死 0/1 的中文映射，
 * 以后加「锁定 / 待激活」这类状态只改后端一处，不用发版前端。
 *
 * @param roles    可选角色，顺序即界面顺序
 * @param statuses 可选状态，顺序即界面顺序
 */
public record UserAdminOptionsVO(List<Option> roles, List<Option> statuses) {

    /**
     * @param code  落库的 int 值，取值见 Roles / UserStatus
     * @param label 中文名，由 Roles.label / UserStatus.label 生成，不在这里再抄一遍
     */
    public record Option(int code, String label) {
    }

    /**
     * @param actorRank 当前操作者的管理层级。roles 只返回**层级严格低于操作者**的角色：
     *                  管理员拿不到「管理员 / 超级管理员」选项，超级管理员拿不到「超级管理员」。
     *                  下拉里根本没有的选项，比「选了才告诉你不行」干净。
     */
    public static UserAdminOptionsVO forActor(Integer actorRank) {
        List<Option> roles = Roles.knownByRankDesc().stream()
                .filter(code -> actorRank != null && Roles.rank(code) < actorRank)
                .map(code -> new Option(code, Roles.label(code)))
                .collect(Collectors.toList());
        return new UserAdminOptionsVO(roles,
                List.of(new Option(UserStatus.ENABLED, UserStatus.label(UserStatus.ENABLED)),
                        new Option(UserStatus.DISABLED, UserStatus.label(UserStatus.DISABLED))));
    }
}
