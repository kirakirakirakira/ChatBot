package com.chatbot.chatbot.entity;

/** 记忆状态。当前只使用 ACTIVE；ARCHIVED 留给以后做「自动衰减」而没有删除风险的场景。 */
public enum MemoryStatus {

    ACTIVE(0, "启用"),
    ARCHIVED(1, "已归档");

    private final int code;
    private final String label;

    MemoryStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }
}
