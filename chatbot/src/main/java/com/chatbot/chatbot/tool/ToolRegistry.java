package com.chatbot.chatbot.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Spring 启动时把所有 Tool Bean 收成注册表；模型只能调用这里存在的工具。 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> discovered) {
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : discovered) {
            Tool previous = map.put(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("工具名重复: " + tool.name());
            }
        }
        this.tools = Map.copyOf(map);
    }

    public List<Tool> all() {
        return List.copyOf(tools.values());
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
