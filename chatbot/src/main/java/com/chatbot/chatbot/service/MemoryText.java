package com.chatbot.chatbot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 记忆文本的规范化、去重与敏感信息过滤。
 * <p>
 * 这些规则必须只有一份：手动新增、用户编辑、自动抽取三条路径都要过同一道闸，
 * 否则「手动加不了的内容，模型能偷偷存进去」只是换了个入口。
 */
final class MemoryText {

    /** 单条记忆最长字符数，与 user_memory.content 的 varchar(500) 对齐。 */
    static final int MAX_CONTENT_LENGTH = 500;

    /**
     * 只挡明显能直接拿来登录 / 付款的秘密。宁可漏掉一些模糊表述，也不要把「我的工号是 123456」
     * 这种正常信息误判成银行卡——误杀会让用户觉得记忆功能坏了。
     */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{16,}"
                    + "|bearer\\s+[a-z0-9._-]{20,}"
                    + "|(password|passwd|pwd|api[_-]?key|secret)\\s*[:=]\\s*\\S+"
                    + "|\\b\\d{17}[\\dXx]\\b"
                    + "|\\b(?:\\d[ -]?){16,19}\\b)");

    private MemoryText() {
    }

    /** 统一空白、去掉控制字符并截断；空白内容返回空串，由调用方拒绝。 */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH).strip();
        }
        return text;
    }

    static boolean hasSecret(String text) {
        return text != null && SECRET_PATTERN.matcher(text).find();
    }

    static String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /** 复用 ChatService 已有的粗略估算，记忆块和摘要块共用同一套预算口径。 */
    static int estimateTokens(String text) {
        return ChatService.estimateTokens(text);
    }
}
