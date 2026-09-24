package com.chatbot.chatbot.dto;

import com.chatbot.chatbot.entity.Attachment;

/**
 * 附件的元信息（不含字节）。出网只给这些 + 一个 id：
 * 图片内容走 {@code GET /api/attachments/{id}} 带 token 单独取，
 * 不走「把 data URL 塞进 JSON」——那会让消息列表的响应体膨胀几十倍。
 *
 * @param size 原始字节数，界面用来显示「1.2 MB」
 */
public record AttachmentVO(Long id, String mime, String fileName, long size) {

    public static AttachmentVO from(Attachment a) {
        return new AttachmentVO(a.getId(), a.getMime(), a.getFileName(), a.getSizeBytes());
    }
}
