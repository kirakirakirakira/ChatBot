package com.chatbot.chatbot.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具执行结果。
 * summary 给人和模型看；data 是结构化明细，落库并在前端可展开。
 */
public record ToolResult(boolean ok, String summary, Map<String, Object> data) {

    public ToolResult {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public static ToolResult ok(String summary, Map<String, Object> data) {
        return new ToolResult(true, summary, data);
    }

    public static ToolResult error(String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("error", summary);
        return new ToolResult(false, summary, data);
    }

    public String status() {
        return ok ? "OK" : "ERROR";
    }
}
