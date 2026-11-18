package com.chatbot.chatbot.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行结果。summary 给人和模型看；data 是结构化明细，落库并回灌模型；
 * ui 是只给前端渲染卡片的小体积展示数据（例如历史检索的引用列表），
 * 刻意不落库、不回灌模型——避免同一份内容在 Prompt 和审计表里各存一遍。
 * <p>
 * citations 是 ui.citations 的类型化版本，只给后端持久化用（见 MessageCitationStore）；
 * 前端拿到的仍然是 ui，不是这个字段。
 */
public record ToolResult(boolean ok, String summary, Map<String, Object> data, Map<String, Object> ui,
                         List<ToolCitation> citations) {

    public ToolResult {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        ui = ui == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(ui));
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    /** 大多数工具没有可渲染的卡片，用这个构造器少写两个 Map.of()/List.of()。 */
    public ToolResult(boolean ok, String summary, Map<String, Object> data) {
        this(ok, summary, data, Map.of(), List.of());
    }

    /** 有前端卡片、但没有需要持久化的引用。 */
    public ToolResult(boolean ok, String summary, Map<String, Object> data, Map<String, Object> ui) {
        this(ok, summary, data, ui, List.of());
    }

    public static ToolResult ok(String summary, Map<String, Object> data) {
        return new ToolResult(true, summary, data);
    }

    public static ToolResult ok(String summary, Map<String, Object> data, Map<String, Object> ui) {
        return new ToolResult(true, summary, data, ui);
    }

    public static ToolResult ok(String summary, Map<String, Object> data, Map<String, Object> ui,
                                List<ToolCitation> citations) {
        return new ToolResult(true, summary, data, ui, citations);
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
