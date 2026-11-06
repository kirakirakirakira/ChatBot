package com.chatbot.chatbot.entity;

import java.util.Locale;

/** 长期记忆的类型。库里存字符串而不是 MySQL ENUM，以后加类型不用 ALTER TABLE。 */
public enum MemoryKind {

    PREFERENCE("偏好"),
    FACT("事实"),
    PROJECT("项目"),
    CONSTRAINT("约束");

    private final String label;

    MemoryKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 非法类型给中文 400 文案；调用方负责捕获并转成 ResponseStatusException。 */
    public static MemoryKind from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("记忆类型不能为空");
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的记忆类型: " + raw);
        }
    }

    /** 展示用：老数据里出现新代码不认识的类型时原样返回，不让整页崩。 */
    public static String labelOf(String raw) {
        try {
            return from(raw).label();
        } catch (IllegalArgumentException e) {
            return raw == null ? "" : raw;
        }
    }
}
