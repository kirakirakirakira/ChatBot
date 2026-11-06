package com.chatbot.chatbot.llm;

import java.util.List;

/**
 * 单次模型调用的选项。把 model / 思考开关 / 思考预算从 streamChat 的形参里收进一个 record，
 * 以后再加参数（temperature 之类）不用改接口签名。
 *
 * @param model          本次使用的模型 id；由 service 层校验过白名单，实现类不再自己决定
 * @param enableThinking 思考开关；null = 不下发 enable_thinking，由服务商默认值决定
 * @param thinkingBudget 思考预算（思维链 token 上限，Chat Completions 的 thinking_budget 参数）；
 *                       null = 不下发，由模型自己决定想多久。上限是各模型的「最大思维链长度」，
 *                       超了百炼会返回 400 并把上限写进错误文案（见 error-code 文档），界面会原样展示
 * @param enableSearch   联网搜索（Chat Completions 的 enable_search 参数）。true 才下发；
 *                       搜索策略固定用默认 turbo（qwen3.8 系在兼容协议下不支持 agent），
 *                       计费约 3 元/千次 + 检索内容带来的输入 token，所以默认关
 * @param tools          本次暴露给模型的工具定义；空列表表示普通聊天
 * @param toolChoice     OpenAI 兼容的 tool_choice；auto / required / none 或具体函数名
 * @param maxTokens      planner 调用的输出上限；普通聊天不传
 */
public record LlmCallOptions(
        String model,
        Boolean enableThinking,
        Integer thinkingBudget,
        Boolean enableSearch,
        List<LlmTool> tools,
        String toolChoice,
        Integer maxTokens) {

    /** 兼容原来的四参构造：普通聊天 / 记忆抽取 / 摘要不传工具。 */
    public LlmCallOptions(String model, Boolean enableThinking, Integer thinkingBudget, Boolean enableSearch) {
        this(model, enableThinking, thinkingBudget, enableSearch, List.of(), null, null);
    }
}
