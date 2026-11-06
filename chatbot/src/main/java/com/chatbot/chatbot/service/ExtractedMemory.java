package com.chatbot.chatbot.service;

/** 自动抽取的一条结果；只在本服务内部流转，不出网。 */
public record ExtractedMemory(
        String action,
        Long id,
        String kind,
        String content,
        Double confidence) {
}
