package com.chatbot.chatbot.dto;

/**
 * 「个人信息」页的使用统计（GET /api/users/me/stats）。
 * <p>
 * 单独一个接口而不是塞进 {@link UserVO}：这三个数都是 count(*)，其中消息数要扫 LONGTEXT 大表，
 * 而 UserVO 出现在**登录**这种热路径上。分开口之后，登录不会被统计拖慢，
 * 统计页也可以自己决定要不要重拉（保存资料后就不必）。
 * <p>
 * 只统计「自己的」数据：三个 count 都带 owner_id 条件，越权统计在这个形状里没有落脚点。
 *
 * @param conversationCount 我的会话数
 * @param messageCount      我的会话里的消息总数（含助手回复）
 * @param attachmentCount   我上传过的图片附件数
 */
public record UserProfileStatsVO(long conversationCount, long messageCount, long attachmentCount) {
}
