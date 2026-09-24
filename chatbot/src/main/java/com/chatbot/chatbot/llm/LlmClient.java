package com.chatbot.chatbot.llm;

import java.util.List;

/**
 * LLM 调用抽象。实现可以是本地 Mock，也可以是任何 OpenAI 兼容服务。
 */
public interface LlmClient {

    /**
     * 流式调用模型：每收到一段增量就按序回调 listener（思考走 onReasoning，回答走 onToken，
     * 结束时如有用量走 onUsage）。正常返回表示生成结束，失败抛异常。
     * 这是阻塞方法，调用方应在虚拟线程中执行。
     *
     * @param options 模型 / 思考开关 / 思考预算，见 {@link LlmCallOptions}
     */
    void streamChat(List<LlmMessage> messages, LlmCallOptions options, LlmStreamListener listener);
}
