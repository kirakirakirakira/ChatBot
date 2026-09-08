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
 * 登录拦截器：/api/** 除了登录接口，全部要求带合法的 Authorization: Bearer token。
 * <p>
 * 抛出的 ResponseStatusException 会被 GlobalExceptionHandler 转成统一的
 * ErrorResponse JSON（401=没登录/登录失效，403=登录了但权限不够），
 * 前端只需要认 status 就能决定「弹回登录页」还是「提示无权限」。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** CurrentUser 在 request attribute 里的 key，见 CurrentUserArgumentResolver。 */
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
        // 不是控制器方法就放行：CORS 预检（OPTIONS）走 PreFlightHandler，
        // 静态资源 / 404 走别的 handler，都不该被登录拦住。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw unauthorized("未登录，请先登录");
        }
        TokenService.TokenPayload payload = tokenService.verify(token);

        // 每个请求都回表查一次用户：多一次主键查询，换来三件事立刻生效 ——
        // 账号被删、角色被改、密码被改（下面那条 iat 比较）。纯无状态 token 做不到这些。
        User user = userRepository.findById(payload.uid())
                .orElseThrow(() -> unauthorized("登录状态已失效（账号不存在），请重新登录"));

        // 两边都换成 epoch 毫秒再比：password_changed_at 是 datetime(6)，带亚秒精度，
        // 而 token 的 iat 若按秒截断，改密码那一秒内签发的旧 token 就能躲过这次失效判断。
        LocalDateTime passwordChangedAt = user.getPasswordChangedAt();
        if (passwordChangedAt != null
                && payload.iat() < passwordChangedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
            throw unauthorized("密码已修改，请重新登录");
        }

        CurrentUser currentUser = new CurrentUser(user.getId(), user.getUsername(), user.getRole());
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
     * 只认 Authorization: Bearer &lt;token&gt;。
     * 不做 ?token= 查询参数兜底：前端的 SSE 也是用 fetch 发的（不是 EventSource），
     * 能带自定义头；而 query 里的 token 会被写进访问日志和浏览器历史，是个纯亏本的口子。
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