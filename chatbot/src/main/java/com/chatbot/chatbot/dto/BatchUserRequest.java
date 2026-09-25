package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理台批量操作请求体（POST /api/admin/users/batch）。
 * <p>
 * **一次只做一个动作**（不是「一组操作」）：界面上的批量就是一排按钮里点一个，
 * 混着传会让「哪一步失败了」变得说不清，回滚语义也无从谈起。
 * <p>
 * 刻意不是一个大事务：批量操作里「某几行注定失败」是常态（自己的行、层级不低于自己的行、
 * 最后一个启用的管理员），整批回滚会让管理员改一个人都得先把不能动的那几行手工挑出去。
 * 所以响应是**逐条结果**（{@link BatchUserResultVO}），成功的落地、失败的带原因回来。
 *
 * @param ids         目标用户 id，1~100 个；重复 id 由 service 去重，不存在的 id 逐条报失败
 * @param action      动作名，取值见 {@link BatchUserAction}；用 String 接是为了给中文 400（理由见该类注释）
 * @param role        action=SET_ROLE 时必填，其余动作忽略；未知值在 service 里 400
 * @param newPassword action=RESET_PASSWORD 且 generate 不为 true 时必填（6~64），此时所有人设成同一个密码
 * @param generate    action=RESET_PASSWORD 时可用：true = 每人一个随机密码，明文只在本次响应里逐条回显
 */
public record BatchUserRequest(
        @NotEmpty(message = "请至少选择一个用户")
        @Size(max = 100, message = "单次批量操作最多 100 个用户")
        List<Long> ids,
        @NotBlank(message = "缺少批量操作类型 action") String action,
        Integer role,
        String newPassword,
        Boolean generate) {
}
