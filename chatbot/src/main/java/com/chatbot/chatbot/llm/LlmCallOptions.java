package com.chatbot.chatbot.llm;

/**
 * 单次模型调用的选项。把 model / 思考开关 / 思考预算从 streamChat 的形参里收进一个 record，
 * 以后再加参数（temperature 之类）不用改接口签名。
 *
 * @param model          本次使用的模型 id；由 service 层校验过白名单，实现类不再自己决定
 * @param enableThinking 思考开关；null = 不下发 enable_thinking，由服务商默认值决定
 * @param thinkingBudget 思考预算（思维链 token 上限，Chat Completions 的 thinking_budget 参数）；
 *                       null = 不下发，由模型自己决定想多久。上限是各模型的「最大思维链长度」，
 *                       超了百炼会返回 400 并把上限写进错误文案（见 error-code 文档），界面会原样展示
 */
public record LlmCallOptions(String model, Boolean enableThinking, Integer thinkingBudget) {
}
