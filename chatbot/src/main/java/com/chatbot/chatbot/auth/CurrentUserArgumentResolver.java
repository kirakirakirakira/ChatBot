package com.chatbot.chatbot.auth;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * 让控制器方法可以直接声明 CurrentUser 形参，不用自己从 request 里掏 attribute。
 * 注册见 WebConfig#addArgumentResolvers。
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                 ModelAndViewContainer mavContainer,
                                 NativeWebRequest webRequest,
                                 WebDataBinderFactory binderFactory) {
        Object attribute = webRequest.getAttribute(
                AuthInterceptor.CURRENT_USER_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (attribute instanceof CurrentUser currentUser) {
            return currentUser;
        }
        // 正常走不到：AuthInterceptor 已经把未登录请求拦在前面了。
        // 这里是兜底 —— 万一以后有人往 excludePathPatterns 里加了路径，
        // 又在那个接口上声明了 CurrentUser，会得到明确的 401 而不是一个 null 引发 NPE。
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录，请先登录");
    }
}