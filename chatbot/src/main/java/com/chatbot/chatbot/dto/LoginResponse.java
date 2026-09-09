package com.chatbot.chatbot.dto;

/**
 * 登录 / 改密码成功后下发的登录态。
 *
 * @param token     后续请求放进 Authorization: Bearer &lt;token&gt;
 * @param tokenType 固定 Bearer
 * @param expiresIn token 有效期（秒）
 * @param user      当前用户信息
 */
public record LoginResponse(String token, String tokenType, long expiresIn, UserVO user) {

    public static LoginResponse of(String token, long expiresIn, UserVO user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}