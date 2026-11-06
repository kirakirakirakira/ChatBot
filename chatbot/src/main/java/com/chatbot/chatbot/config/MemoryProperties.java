package com.chatbot.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 记忆与摘要配置。所有开关都有默认值，老部署升级后不用改 application.properties 也能启动。
 *
 * @param enabled              总开关；false 时既不抽取也不注入，手动记忆仍可在设置页管理
 * @param extractionModel      自动抽取使用的模型；留空或不在 llm.available-models 时回落 llm.model
 * @param summaryModel         会话摘要使用的模型；规则同上
 * @param maxPerTurn           每轮最多应用几条抽取结果，防止模型一次吐 50 条
 * @param maxActive            单用户最多保留多少条启用记忆
 * @param injectLimit          每轮最多注入几条记忆
 * @param injectMaxTokens      记忆块最多占多少输入 token
 * @param summaryTriggerMessages 未摘要消息达到多少条时触发摘要
 * @param summaryTriggerTokens   未摘要消息估算 token 达到多少时触发摘要
 * @param summaryBatchMessages   每次摘要最多压缩多少条旧消息
 * @param summaryMaxChars        摘要最大字符数；超出截断，防止摘要自己把上下文吃掉
 * @param minConfidence          自动抽取结果的最低置信度
 */
@ConfigurationProperties(prefix = "memory")
public record MemoryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("qwen3.8-flash") String extractionModel,
        @DefaultValue("qwen3.8-flash") String summaryModel,
        @DefaultValue("3") int maxPerTurn,
        @DefaultValue("100") int maxActive,
        @DefaultValue("20") int injectLimit,
        @DefaultValue("1200") int injectMaxTokens,
        @DefaultValue("20") int summaryTriggerMessages,
        @DefaultValue("8000") int summaryTriggerTokens,
        @DefaultValue("20") int summaryBatchMessages,
        @DefaultValue("800") int summaryMaxChars,
        @DefaultValue("0.70") double minConfidence) {
}
