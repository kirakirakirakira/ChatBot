package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 本人改自己的资料（PUT /api/users/me/profile）。
 * <p>
 * 三个字段都是「传 null / 全空白 = 清空」，service 统一 strip 后存 NULL：
 * 库里存空串会让「没填」和「填了个空」变成两种状态，界面判断得多写一倍分支。
 * <p>
 * 刻意**不能改 username**：登录名是审计日志（actor_name / target_name）和「谁干的」这条线索的锚点，
 * 允许本人随时改，等于允许他把历史记录里的自己抹掉。要改名请让管理员重建账号。
 * <p>
 * role / status 也不在这里：那是管理端的写口径（/api/admin/users/{id}/role|status），
 * 放进「改自己的资料」就是一个现成的提权洞。
 *
 * @param nickname 昵称，最长 50（与列宽一致）；空 = 清掉，界面回退显示 username
 * @param email    邮箱，最长 100；@Email 对 null 和空串都放行，所以「清空」不会被格式校验挡住
 * @param phone    联系电话，最长 30；只卡长度不卡格式，理由见实体 User.phone 的注释
 */
public record UpdateProfileRequest(
        @Size(max = 50, message = "昵称长度需在 0~50 之间") String nickname,
        @Size(max = 100, message = "邮箱长度需在 0~100 之间")
        @Email(message = "邮箱格式不正确") String email,
        @Size(max = 30, message = "手机号长度需在 0~30 之间") String phone) {
}
