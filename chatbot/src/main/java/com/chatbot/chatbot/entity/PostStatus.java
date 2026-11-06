package com.chatbot.chatbot.entity;

/**
 * 博客文章状态。
 * <p>
 * 落库是 {@code varchar(20)} 而不是 MySQL ENUM：状态一定会随审批 / 下架 / 草稿逐步增加，
 * ENUM 每加一个值都要手工 ALTER TABLE（{@code message.role} 已经吃过这个亏，见 PROJECT_OVERVIEW 13.2）。
 * 一期只有直接发布，其余值先定义好，二期接审批时不用改表。
 */
public enum PostStatus {

    /** 草稿：作者自己可见，一期不提供入口，留给「保存草稿」。 */
    DRAFT("草稿"),

    /** 待审核：开启审核后新建文章进入这里，普通用户列表看不到。 */
    PENDING("待审核"),

    /** 已发布：所有登录用户可见。 */
    PUBLISHED("已发布"),

    /** 已驳回：只有作者在「我的文章」里看得到，可按驳回原因修改后重提。 */
    REJECTED("已驳回"),

    /** 已下架：管理员强制隐藏，文章仍在库里，可恢复。 */
    HIDDEN("已下架");

    private final String label;

    PostStatus(String label) {
        this.label = label;
    }

    /** 中文名由后端给，前端不维护一份枚举到中文的映射（和 Roles / UserStatus 同一口径）。 */
    public String label() {
        return label;
    }
}