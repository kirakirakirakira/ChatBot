package com.chatbot.chatbot.dto;

import java.util.List;

/**
 * 批量操作的逐条结果。
 * <p>
 * 为什么不是「成功 N 个」一个数字：批量里失败是常态（自己的行 / 层级不够 / 最后一个管理员），
 * 只给总数的话管理员得自己回列表里猜是哪几行没动、为什么没动。逐条给原因，
 * 界面可以直接列出来，也省掉一次「刷新后逐行核对」。
 * <p>
 * requested 是**去重后**实际处理的数量，可能小于请求里的 ids.size()（前端多选 + 翻页容易带进重复 id）。
 *
 * @param requested 实际处理的用户数（去重后）
 * @param succeeded 成功条数
 * @param failed    失败条数，恒等于 requested - succeeded
 * @param items     逐条结果，顺序与去重后的入参一致
 */
public record BatchUserResultVO(int requested, int succeeded, int failed, List<Item> items) {

    /**
     * @param id                目标用户 id
     * @param username          用户名快照；id 不存在时为 null（这一条必然失败）
     * @param success           这一条成没成
     * @param message           失败原因（中文，直接展示）；成功时为 null
     * @param generatedPassword 只在 RESET_PASSWORD + generate=true 的成功条上有值，**只回显这一次**
     * @param user              成功且动作会改变用户行时回传最新的一行，前端就地替换、不用整页重拉；
     *                          删除 / 强制下线 / 重置密码 这类没有「新行」可回的动作是 null
     */
    public record Item(Long id,
                       String username,
                       boolean success,
                       String message,
                       String generatedPassword,
                       AdminUserVO user) {

        public static Item ok(Long id, String username, AdminUserVO user) {
            return new Item(id, username, true, null, null, user);
        }

        public static Item okWithPassword(Long id, String username, String generatedPassword) {
            return new Item(id, username, true, null, generatedPassword, null);
        }

        public static Item fail(Long id, String username, String message) {
            return new Item(id, username, false, message, null, null);
        }
    }

    public static BatchUserResultVO of(List<Item> items) {
        int succeeded = (int) items.stream().filter(Item::success).count();
        return new BatchUserResultVO(items.size(), succeeded, items.size() - succeeded, items);
    }
}
