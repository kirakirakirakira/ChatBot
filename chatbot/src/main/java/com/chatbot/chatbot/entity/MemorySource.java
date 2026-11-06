package com.chatbot.chatbot.entity;

/** 记忆来源：自动抽取 / 用户手动维护。手动维护的记忆不允许被自动抽取覆盖。 */
public enum MemorySource {

    AUTO(0, "自动"),
    MANUAL(1, "手动");

    private final int code;
    private final String label;

    MemorySource(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return AUTO.label;
        }
        for (MemorySource source : values()) {
            if (source.code == code) {
                return source.label;
            }
        }
        return "未知";
    }
}
