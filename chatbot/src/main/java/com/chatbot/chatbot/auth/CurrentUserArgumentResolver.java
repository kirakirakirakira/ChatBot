package com.chatbot.chatbot.auth;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/** 让控制器方法直接声明 CurrentUser 形参，注册见 WebConfig。 */
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
        // 兜底：正常走不到。万一某条白名单路径上的接口声明了 CurrentUser，给明确的 401 而不是 NPE
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录，请先登录");
    }
}