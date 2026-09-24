package com.chatbot.chatbot.config;

import com.chatbot.chatbot.auth.AuthInterceptor;
import com.chatbot.chatbot.auth.CurrentUserArgumentResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** 全局 CORS + 登录拦截器 / 参数解析器注册。 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    /**
     * 免登录路径只有登录接口本身：/api/auth/me 也要登录，它的作用就是验证 token 还有效没有。
     */
    private static final String[] PUBLIC_PATHS = {"/api/auth/login"};

    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    /**
     * 构造时就取一次白名单：cors.allowed-origins 配漏了（全空白）要在启动时炸，
     * 而不是等第一个跨域请求进来才发现「怎么谁都不放行」。
     */
    private final String[] allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     CurrentUserArgumentResolver currentUserArgumentResolver,
                     CorsProperties corsProperties) {
        this.authInterceptor = authInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.allowedOrigins = corsProperties.origins();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 白名单来自 cors.allowed-origins（默认只有 Vite 的 5173），刻意不用 "*"：
        // 那等于允许任何网站的脚本带着用户 token 调 /api，只适合内网开发，
        // 不该是「配置漏写」时的默认结果。
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * /api/** 一律要求登录，PUBLIC_PATHS 例外。新加的接口默认就是「要登录」，
     * 不用记着往白名单里补一行——白名单越长越容易漏，漏一个就是裸奔的接口。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }

    /** 控制器方法声明 CurrentUser 形参即可拿到当前登录用户。 */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
