package com.chatbot.chatbot.auth;

import com.chatbot.chatbot.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 登录 token 的签发与校验：自签的 HMAC-SHA256，形如
 * base64url(payloadJson) + "." + base64url(signature)，跟 JWT 的 HS256 一个思路。
 * <p>
 * 为什么不用 JWT 库、也不在数据库里存 session：
 * <ul>
 *   <li>不引依赖：签名只用 JDK 的 Mac，序列化用项目里已有的 Jackson；</li>
 *   <li>无状态：不用建 token 表、不用定时清过期行，后端重启也不掉登录态；</li>
 *   <li>该失效时照样能失效：payload 带 iat，配合 sys_user.password_changed_at，
 *       改完密码后旧 token 立刻作废（判断在 AuthInterceptor 里）。</li>
 * </ul>
 */
@Component
public class TokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * iat / exp 用 epoch 毫秒而不是秒：sys_user.password_changed_at 是 datetime(6)，
     * 按秒比较的话，「改密码那一秒内签发的旧 token」会躲过失效判断；
     * 而改成 <= 又会把改密码接口自己刚换发的新 token（同一秒）一起干掉。
     * 毫秒粒度才能把这两者分开。
     *
     * @param uid  用户 id，校验时会回表查一次，确认账号还在、角色没变
     * @param iat  签发时间（epoch 毫秒），用来和 sys_user.password_changed_at 比
     * @param exp  过期时间（epoch 毫秒）
     */
    public record TokenPayload(long uid, String username, int role, long iat, long exp) {
    }

    private final ObjectMapper objectMapper;
    private final SecretKeySpec key;
    private final long ttlSeconds;

    public TokenService(AuthProperties properties, ObjectMapper objectMapper) {
        String secret = properties.tokenSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.token-secret 未配置，无法签发登录 token");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "auth.token-secret 至少 " + MIN_SECRET_LENGTH + " 字符，当前太短；生产环境请用 AUTH_TOKEN_SECRET 环境变量覆盖");
        }
        this.objectMapper = objectMapper;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.ttlSeconds = properties.tokenTtlHours() * 3600L;
    }

    /** token 有效期（秒），下发给前端做展示 / 提示用。 */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(User user) {
        long now = Instant.now().toEpochMilli();
        TokenPayload payload = new TokenPayload(
                user.getId(), user.getUsername(), user.getRole(), now, now + ttlSeconds * 1000L);
        String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        return body + "." + ENCODER.encodeToString(sign(body));
    }

    /**
     * 校验签名和有效期。任何一步不过都抛 401，
     * 文案经 GlobalExceptionHandler 变成统一的 ErrorResponse JSON 给前端。
     * <p>
     * 注意这里只看「token 本身可不可信」，账号是否还存在 / 是否改过密码由 AuthInterceptor 回表判断。
     */
    public TokenPayload verify(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw unauthorized("登录凭证格式不正确，请重新登录");
        }
        String body = token.substring(0, dot);
        byte[] signature;
        TokenPayload payload;
        try {
            signature = DECODER.decode(token.substring(dot + 1));
            // Jackson 3 的异常和 Base64 的 IllegalArgumentException 都是 RuntimeException，
            // 统一在这儿兜住：解析不了就等于凭证不可信，不必把内部异常抛给调用方。
            if (!MessageDigest.isEqual(signature, sign(body))) {
                throw unauthorized("登录凭证无效，请重新登录");
            }
            payload = objectMapper.readValue(DECODER.decode(body), TokenPayload.class);
        } catch (IllegalArgumentException e) {
            throw unauthorized("登录凭证无效，请重新登录");
        }
        if (payload.exp() < Instant.now().toEpochMilli()) {
            throw unauthorized("登录已过期，请重新登录");
        }
        return payload;
    }

    /**
     * 常量时间比较签名（上面用的 MessageDigest.isEqual），
     * 避免用「多久返回」逐字节猜出正确签名。
     */
    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 是 JDK 必备算法，走到这里说明环境有问题，属于启动级错误
            throw new IllegalStateException("token 签名失败", e);
        }
    }

    private static ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}