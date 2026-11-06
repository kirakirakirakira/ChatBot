package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.entity.PostStatus;
import org.springframework.stereotype.Component;

/**
 * 发布策略：**所有「谁能发、发出来是什么状态」的判断只在这里**。
 * <p>
 * 一期所有启用用户都能直接发布，所以方法体只有一行。以后加审批 / 权限时改这里，不要去
 * Controller 或 BlogService 里另写一个 if：
 * <ul>
 *   <li>二期：读 {@code blog.require-review}，为 true 时返回 {@link PostStatus#PENDING}；</li>
 *   <li>三期：查「该用户是否有发布权限」，没有就抛 403；有权限但需审核则返回 PENDING。</li>
 * </ul>
 * 放在 Service 层而不是 AuthInterceptor：拦截器管「这个接口要不要管理员」，
 * 「这个用户能不能发内容」是内容业务规则，和会话归属校验同一层级。
 */
@Component
public class BlogPolicy {

    public PostStatus initialStatus(CurrentUser author) {
        return PostStatus.PUBLISHED;
    }
}