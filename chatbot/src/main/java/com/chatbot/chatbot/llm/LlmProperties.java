package com.chatbot.chatbot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.properties 中 llm.* 配置。
 *
 * @param maxHistoryMessages   每轮送给模型的最近历史条数上限，&lt;=0 表示不限制；防止长会话把上下文窗口和 token 一起撑爆。
 * @param enableThinking       思考开关默认值，映射为请求体顶层的 enable_thinking（百炼 / Qwen 系参数），单次请求可用请求体里的 enableThinking 覆盖。
 *                             true 时思考增量以 SSE reasoning 事件实时推给前端；false 时首字延迟降到秒级、也省掉按输出计费的思考 token；
 *                             null 则不下发该参数，由服务商默认值决定；服务商不认识这个字段时把配置行注释掉即可。
 * @param requestTimeoutSeconds 单次模型调用的总超时（秒），是「整轮生成」的上限而不是空闲超时，到点 JDK HttpClient 会直接关掉流式响应体。
 *                             SSE 超时自动取它再加 30 秒余量，让「模型超时」的错误文案先冒出来，而不是连接被静默掐断。
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String model,
        @DefaultValue("20") int maxHistoryMessages,
        Boolean enableThinking,
        @DefaultValue("900") int requestTimeoutSeconds) {
}
