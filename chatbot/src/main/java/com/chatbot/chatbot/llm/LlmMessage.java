package com.chatbot.chatbot.llm;

import com.chatbot.chatbot.entity.Attachment;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 传给 LLM 的一条消息（OpenAI 格式）。
 * <p>
 * content 的类型是 Object 而不是 String：纯文本消息要发字符串，带图片的消息要发
 * {@code List<LlmContentPart>}。两种形状 OpenAI 兼容协议都认，用 Object 让序列化自己去分派，
 * 比给两种消息各建一个类省事，也不会让纯文本消息平白多一层数组。
 *
 * @param reasoningContent 助手消息的历史思考过程。qwen3.8-max / qwen3.8-flash 的 preserve_thinking 默认 true，
 *                         要求把历史 reasoning_content 完整回传（官方 Chat 文档）；没有就为 null、不下发该字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmMessage(
        String role,
        Object content,
        @JsonProperty("reasoning_content") String reasoningContent) {

    /** 不带思考的普通消息。 */
    public static LlmMessage of(String role, String content) {
        return new LlmMessage(role, content, null);
    }

    /**
     * 带图片的用户消息：图片段在前、文本段在后（Qwen-VL 官方示例的顺序），
     * 模型先看到图再读到「针对这张图的问题」。文本为空时只发图片段——纯图提问是合法用法。
     */
    public static LlmMessage multimodal(String role, String text, List<Attachment> images, String reasoningContent) {
        List<LlmContentPart> parts = new ArrayList<>(images.size() + 1);
        for (Attachment a : images) {
            parts.add(LlmContentPart.image(toDataUrl(a)));
        }
        if (text != null && !text.isBlank()) {
            parts.add(LlmContentPart.text(text));
        }
        return new LlmMessage(role, parts, reasoningContent);
    }

    /** 附件转 base64 data URL。附件存在自己库里，没有公网地址，只能内联。 */
    private static String toDataUrl(Attachment a) {
        return "data:" + a.getMime() + ";base64," + Base64.getEncoder().encodeToString(a.getData());
    }

    /**
     * 取纯文本内容：多模态时把所有 text 段拼起来，图片段忽略。
     * 给 Mock 客户端和日志用，永不为 null。
     */
    public String text() {
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof LlmContentPart part && part.text() != null) {
                    sb.append(part.text());
                }
            }
            return sb.toString();
        }
        return "";
    }

    /** 这条消息带了几张图。 */
    public int imageCount() {
        if (!(content instanceof List<?> parts)) {
            return 0;
        }
        int n = 0;
        for (Object p : parts) {
            if (p instanceof LlmContentPart part && part.isImage()) {
                n++;
            }
        }
        return n;
    }

    public boolean hasImages() {
        return imageCount() > 0;
    }
}
