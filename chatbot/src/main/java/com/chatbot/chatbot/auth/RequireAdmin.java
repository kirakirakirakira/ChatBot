package com.chatbot.chatbot.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 打在控制器类或方法上：只有 role=1（管理员）能调用，普通用户拿 403。
 * 由 AuthInterceptor 统一检查，控制器里不用再写一遍权限判断。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
}