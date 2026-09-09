package com.chatbot.chatbot.config;

import com.chatbot.chatbot.auth.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    /**
     * BCrypt（默认 strength=10）。
     * 项目只引了 spring-security-crypto、没有 starter-security，所以这个 bean 得自己声明；
     * 也是有意不让 Spring Security 自动接管全部请求，免得和 SSE 流式接口、自定义拦截器打架。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}