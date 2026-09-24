package com.chatbot.chatbot.llm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 多模态消息 content 数组里的一段（OpenAI Chat Completions 格式）。
 * <p>
 * 文本段形如 {@code {"type":"text","text":"..."}}，图片段形如
 * {@code {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}}。
 * NON_NULL 保证两种段各自只出现自己那两个字段——多下发一个 null 字段有些服务商会直接 400。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmContentPart(
        String type,
        String text,
        @JsonProperty("image_url") ImageUrl imageUrl) {

    /** 图片地址。这里只用 base64 data URL：附件存在自己库里，没有可公网访问的地址可给。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImageUrl(String url) {
    }

    public static LlmContentPart text(String text) {
        return new LlmContentPart("text", text, null);
    }

    public static LlmContentPart image(String dataUrl) {
        return new LlmContentPart("image_url", null, new ImageUrl(dataUrl));
    }

    /**
     * 是否图片段。必须 @JsonIgnore：百炼对 content 数组的元素做严格校验（pydantic），
     * 多出一个它不认识的 "image": true 字段就整个请求 400，错误文案还是一串 validation errors，极难看出根因。
     */
    @JsonIgnore
    public boolean isImage() {
        return "image_url".equals(type);
    }
}
