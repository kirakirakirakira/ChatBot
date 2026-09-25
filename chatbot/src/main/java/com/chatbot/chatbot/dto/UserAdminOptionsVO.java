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
 * @param roles    **可指派**的角色（层级严格低于操作者），给「建号 / 改角色」的下拉用，顺序即界面顺序
 * @param allRoles 全部已知角色（层级从高到低），给列表的**角色筛选器**用
 * @param statuses 可选状态，顺序即界面顺序
 */
public record UserAdminOptionsVO(List<Option> roles, List<Option> allRoles, List<Option> statuses) {

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
     *                  <p>
     *                  allRoles 刻意**不按层级过滤**：筛选是读操作，列表里本来就看得见管理员 / 超管的行
     *                  （只是控件锁死），筛选器少了这两档就成了「看得见、筛不出」。
     *                  「能不能指派」和「能不能筛」是两件事，用两个字段分开表达，别复用同一份数据。
     */
    public static UserAdminOptionsVO forActor(Integer actorRank) {
        List<Option> allRoles = Roles.knownByRankDesc().stream()
                .map(code -> new Option(code, Roles.label(code)))
                .collect(Collectors.toList());
        List<Option> assignable = allRoles.stream()
                .filter(option -> actorRank != null && Roles.rank(option.code()) < actorRank)
                .collect(Collectors.toList());
        return new UserAdminOptionsVO(assignable, allRoles,
                List.of(new Option(UserStatus.ENABLED, UserStatus.label(UserStatus.ENABLED)),
                        new Option(UserStatus.DISABLED, UserStatus.label(UserStatus.DISABLED))));
    }
}
