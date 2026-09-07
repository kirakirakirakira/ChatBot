package com.chatbot.chatbot.llm;

import java.util.List;

/**
 * LLM 调用抽象。实现可以是本地 Mock，也可以是任何 OpenAI 兼容服务。
 */
public interface LlmClient {

    /**
     * 流式调用模型：每收到一段增量文本就按序回调 listener。
     * 思考过程走 onReasoning()，正式回答走 onToken()。
     * 正常返回表示生成结束；失败抛异常。
     * 注意：这是阻塞方法，调用方应在虚拟线程中执行。
     *
     * @param enableThinking 本次调用的思考开关（可选）：非 null 时覆盖 llm.enable-thinking 配置，
     *                       null 表示沿用配置；两者都是 null 时不下发该参数，由服务商默认值决定。
     */
    void streamChat(List<LlmMessage> messages, Boolean enableThinking, LlmStreamListener listener);
}
