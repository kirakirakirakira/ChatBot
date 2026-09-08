package com.chatbot.chatbot.dto;

/**
 * 登录成功 / 改密码成功后下发的登录态。
 *
 * @param token     后续请求放进 Authorization: Bearer &lt;token&gt;
 * @param tokenType 固定 Bearer
 * @param expiresIn token 有效期（秒），前端可用来做「快过期了」的提示
 * @param user      当前用户信息（id / username / role / roleLabel / createdAt）
 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserVO user) {

    public static LoginResponse of(String token, long expiresIn, UserVO user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}