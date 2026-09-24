package com.chatbot.chatbot.dto;

import java.util.List;

/**
 * 发送消息的请求体。
 *
 * @param message        用户消息。刻意不用 @NotBlank：纯图片提问（message 为空 + 带附件）是合法用法，
 *                       「文本和图片不能同时为空」这条规则放在 ChatService 里判，报同一个 400 + 同一句中文文案。
 * @param enableThinking 本次请求的思考开关，true / false 只对本条消息生效并覆盖 llm.enable-thinking；为 null 时沿用服务端配置。
 * @param attachmentIds  随本条消息发送的图片附件 id（来自 POST /api/conversations/{id}/attachments）。
 *                       null / 空 = 纯文本消息；非空时模型必须支持图片输入，否则 400。
 */
public record ChatRequest(
        String message,
        Boolean enableThinking,
        /** 本次使用的模型 id；null = 用服务端 llm.model。必须落在 llm.available-models 白名单里，否则 400。 */
        String model,
        /** 思考预算（思维链 token 上限）；null = 不下发 thinking_budget。思考关闭时后端忽略它。 */
        Integer thinkingBudget,
        /** 联网搜索开关；null / false = 不搜索。搜索按次计费（turbo 约 3 元/千次），所以默认关。 */
        Boolean enableSearch,
        List<Long> attachmentIds) {
}
