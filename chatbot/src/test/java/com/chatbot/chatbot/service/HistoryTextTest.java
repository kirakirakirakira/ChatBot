package com.chatbot.chatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 摘要截取是纯函数，独立测：它是检索结果里唯一会让用户「断章取义」的地方。 */
class HistoryTextTest {

    @Test
    void keepsKeywordInSnippetAndAddsEllipsis() {
        String content = "前面是一大段无关的铺垫。".repeat(10) + "我们当时讨论的是 SSE 断线重连。"
                + "后面还有很多别的内容。".repeat(10);
        String snippet = HistorySearchService.snippet(content, "SSE", 60);
        assertTrue(snippet.contains("SSE 断线重连"));
        assertTrue(snippet.startsWith("…"));
        assertTrue(snippet.endsWith("…"));
        assertTrue(snippet.length() <= 62);
    }

    @Test
    void collapsesWhitespaceAndKeepsShortTextIntact() {
        String snippet = HistorySearchService.snippet("  第一行\n\n第二行\t第三行  ", "第二行", 80);
        assertEquals("第一行 第二行 第三行", snippet);
        assertFalse(snippet.contains("\n"));
    }

    @Test
    void fallsBackToHeadWhenKeywordDoesNotAppear() {
        assertEquals("完全无关的一段话", HistorySearchService.snippet("完全无关的一段话", "量子纠缠", 80));
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", HistorySearchService.snippet(null, "x", 80));
        assertEquals("", HistorySearchService.snippet("   ", "x", 80));
    }

    @Test
    void neverSplitsSurrogatePair() {
        String content = "😀".repeat(80) + "关键词" + "😀".repeat(80);
        String snippet = HistorySearchService.snippet(content, "关键词", 41);
        assertFalse(snippet.isEmpty());
        for (int i = 0; i < snippet.length(); i++) {
            char c = snippet.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertTrue(i + 1 < snippet.length() && Character.isLowSurrogate(snippet.charAt(i + 1)));
            }
            if (Character.isLowSurrogate(c)) {
                assertTrue(i > 0 && Character.isHighSurrogate(snippet.charAt(i - 1)));
            }
        }
    }
}
