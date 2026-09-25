package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;

import java.util.List;

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

    public static UserAdminOptionsVO defaults() {
        return new UserAdminOptionsVO(
                List.of(new Option(Roles.USER, Roles.label(Roles.USER)),
                        new Option(Roles.ADMIN, Roles.label(Roles.ADMIN))),
                List.of(new Option(UserStatus.ENABLED, UserStatus.label(UserStatus.ENABLED)),
                        new Option(UserStatus.DISABLED, UserStatus.label(UserStatus.DISABLED))));
    }
}
