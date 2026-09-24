package com.chatbot.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * application.properties 中 cors.* 配置。
 *
 * @param allowedOrigins 允许跨域调用 /api 的前端源，逗号分隔可配多个。
 *                       开发默认只有 Vite 的 5173；对外部署用环境变量 CORS_ALLOWED_ORIGINS 覆盖成真实域名。
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    /**
     * 收紧后的白名单：去掉空白项后返回。
     * <p>
     * 全空白时抛异常让启动失败，而不是退化成「全开」：CORS 配漏了的默认结果不该是 *，
     * 那等于把 /api 对任何网站的脚本敞开。报错文案里直接写了该怎么配，别让人去翻文档。
     */
    public String[] origins() {
        List<String> cleaned = (allowedOrigins == null)
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (cleaned.isEmpty()) {
            throw new IllegalStateException(
                    "cors.allowed-origins 不能为空：至少配一个前端源（开发用 http://localhost:5173，"
                            + "多个用逗号分隔），生产用 CORS_ALLOWED_ORIGINS 环境变量覆盖；刻意不提供「全开」的默认值");
        }
        return cleaned.toArray(new String[0]);
    }
}
