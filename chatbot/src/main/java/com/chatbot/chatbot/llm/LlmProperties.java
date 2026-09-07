package com.chatbot.chatbot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.properties 中 llm.* 配置。
 *
 * @param maxHistoryMessages   每轮送给模型的最近历史条数上限，<=0 表示不限制。
 *                             原来是不管会话多长都把全部历史发过去，
 *                             几十轮之后 token 线性膨胀，最终会撞上下文窗口直接报错。
     * @param enableThinking       推理模型的思考开关默认值，映射为请求体顶层的 enable_thinking。
     *                             单次请求可用请求体里的 enableThinking 字段覆盖（true/false）；
     *                             请求没传时沿用此配置。
 *                             true  = 允许思考，思考增量以 SSE reasoning 事件实时推给前端；
 *                             false = 关闭思考，首字延迟从「思考完才开始」（实测约 110 秒）
 *                                     降到秒级，同时省掉思考 token
 *                                     （实测一轮思考 2.4 万字按输出计费、正式回答只有 1 个字）。
 *                             null  = 配置里没写这一项，不下发该参数，由服务商默认值决定。
 *                             enable_thinking 是百炼（DashScope）/ Qwen 系参数，
 *                             直连会拒绝未知字段的服务商时，把配置行注释掉即可。
 * @param requestTimeoutSeconds 单次模型调用的总超时（秒），对应 HttpRequest.timeout()。
 *                             这个值是「整轮生成」的上限，不是空闲超时：
 *                             JDK HttpClient 到点会直接关掉流式响应体。
 *                             原来写死 5 分钟，推理模型思考超过 5 分钟就会在第 300 秒被掐断
 *                             （实测思考到 31407 字、正式回答 0 字，全部作废还照样计费）。
 *                             默认 900 秒；SSE 超时会自动取它再加 30 秒余量，
 *                             让「模型超时」这条错误先冒出来，而不是连接被静默掐断。
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
