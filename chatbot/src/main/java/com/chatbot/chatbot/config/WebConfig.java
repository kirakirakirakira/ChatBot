package com.chatbot.chatbot.config;

import com.chatbot.chatbot.auth.AuthInterceptor;
import com.chatbot.chatbot.auth.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** 全局 CORS + 登录拦截器 / 参数解析器注册。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 免登录路径只有登录接口本身：/api/auth/me 也要登录，它的作用就是验证 token 还有效没有。
     */
    private static final String[] PUBLIC_PATHS = {"/api/auth/login"};

    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(AuthInterceptor authInterceptor,
                     CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.authInterceptor = authInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
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