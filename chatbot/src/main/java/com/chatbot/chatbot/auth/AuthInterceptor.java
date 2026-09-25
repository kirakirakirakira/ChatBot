package com.chatbot.chatbot.auth;

import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 登录拦截器：除登录接口外，/api/** 一律要求合法的 Authorization: Bearer token。
 * 401=未登录/登录失效、403=权限不足，统一由 GlobalExceptionHandler 转成 ErrorResponse JSON。
 * {@code @RequireAdmin} 的判定是「管理员或超级管理员」（Roles.canAccessAdmin）；
 * 进门之后「谁能管谁」由 AdminUserService 的层级规则再算一层。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** CurrentUser 在 request attribute 里的 key。 */
    public static final String CURRENT_USER_ATTRIBUTE = "chatbot.currentUser";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserRepository userRepository;

    public AuthInterceptor(TokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非控制器方法直接放行：CORS 预检、静态资源、404 都不该被登录拦住
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw unauthorized("未登录，请先登录");
        }
        TokenService.TokenPayload payload = tokenService.verify(token);

        // 回表查一次用户，让删号、改角色、改密码能立刻生效（纯无状态 token 做不到）
        User user = userRepository.findById(payload.uid())
                .orElseThrow(() -> unauthorized("登录状态已失效（账号不存在），请重新登录"));

        // 禁用必须「立刻生效」：拦截器每请求回表，所以不用等 token 自然过期，下一个请求就进不来。
        // 正在跑的 SSE 不会被打断（拦截器只在请求开始时执行）——已知限制，见 PROJECT_OVERVIEW 13.1。
        if (!UserStatus.isEnabled(user.getStatus())) {
            throw unauthorized("账号已被禁用，请联系管理员");
        }

        // 两边都换成 epoch 毫秒再比：秒级截断会让改密码当秒签发的旧 token 躲过失效判断
        LocalDateTime passwordChangedAt = user.getPasswordChangedAt();
        if (passwordChangedAt != null
                && payload.iat() < passwordChangedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
            throw unauthorized("密码已修改，请重新登录");
        }

        CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), user.getRole(), user.getSystemPrompt());
        if (requiresAdmin(handlerMethod) && !currentUser.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }

        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        return true;
    }

    private static boolean requiresAdmin(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);
    }

    /**
     * 只认 Authorization: Bearer &lt;token&gt;；不做 ?token= 兜底，免得 token 落进访问日志和浏览器历史。
     */
    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}