package com.chatbot.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Agent 工具调用配置。
 *
 * @param enabled           总开关；false 时完全退回普通聊天
 * @param plannerModel      planner 使用的模型；不在 llm.available-models 时回落 llm.model
 * @param maxSteps          最多执行几轮工具调用
 * @param toolTimeoutSeconds 单个工具超时
 * @param maxResultChars    回灌给模型的单个工具结果上限（完整结果会落库）
 * @param plannerMaxTokens  planner 输出上限，防止规划阶段生成长篇正文
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("qwen3.8-flash") String plannerModel,
        @DefaultValue("5") int maxSteps,
        @DefaultValue("20") int toolTimeoutSeconds,
        @DefaultValue("4000") int maxResultChars,
        @DefaultValue("512") int plannerMaxTokens) {
}
