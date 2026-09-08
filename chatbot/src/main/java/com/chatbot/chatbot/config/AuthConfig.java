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
     * <p>
     * 项目只引了 spring-security-crypto 这一个模块，没有 spring-boot-starter-security，
     * 所以不会有 Spring Security 的自动配置替我们声明这个 bean，得自己来。
     * 这也是有意为之：完整的 Spring Security 会自动接管所有请求，
     * 和现有的 SSE 流式接口、自定义拦截器打架，为一个登录功能不值当。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}