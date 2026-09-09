package com.chatbot.chatbot.llm;

/**
 * 流式回调。推理模型把「思考过程」和「正式回答」分成两个字段推，这里对应两个回调。
 * 非推理模型、或 llm.enable-thinking=false 时不会回调 onReasoning。
 */
public interface LlmStreamListener {

    /** 思考过程的增量文本（reasoning_content）。默认空实现，不关心思考的调用方无需重写。 */
    default void onReasoning(String text) {
    }

    /** 正式回答的增量文本（content）。 */
    void onToken(String text);
}
