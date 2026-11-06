package com.chatbot.chatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 记忆文本规则是手动 / 自动两条写入路径共用的闸门，先用手测锁住几条底线。 */
class MemoryTextTest {

    @Test
    void normalizeCollapsesWhitespaceAndTruncates() {
        assertEquals("我喜欢 Java 26", MemoryText.normalize("  我喜欢\n Java\t26  "));
        assertEquals(MemoryText.MAX_CONTENT_LENGTH, MemoryText.normalize("a".repeat(800)).length());
    }

    @Test
    void detectsObviousSecrets() {
        assertTrue(MemoryText.hasSecret("我的 key 是 sk-1234567890abcdef"));
        assertTrue(MemoryText.hasSecret("password=abcdef123456"));
        assertTrue(MemoryText.hasSecret("银行卡 6222 0202 0000 1234 567"));
        assertFalse(MemoryText.hasSecret("我长期使用 Java 26 + Spring Boot 4"));
    }

    @Test
    void hashIsStableAndSha256Length() {
        assertEquals(MemoryText.hash("同一个内容"), MemoryText.hash("同一个内容"));
        assertEquals(64, MemoryText.hash("同一个内容").length());
        assertFalse(MemoryText.hash("A").equals(MemoryText.hash("B")));
    }
}
