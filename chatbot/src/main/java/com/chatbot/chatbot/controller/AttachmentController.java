package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.entity.Attachment;
import com.chatbot.chatbot.service.AttachmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 附件字节出口。只有一个方法，路径也不在 /api/conversations 下，所以不写类级 @RequestMapping。
 */
@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * 取图片字节。默认受保护（不在 WebConfig.PUBLIC_PATHS 里），必须带 Authorization 头。
     * <p>
     * 这就是为什么前端不能用 {@code <img src="/api/attachments/1">} 直接显示：
     * img 标签带不了自定义头。前端改成 fetch 拿 blob 再转 objectURL——
     * 换来的是「附件 URL 不出现在任何日志 / 浏览器历史 /  Referer 里」，也不需要做 ?token= 兜底
     * （那条路等于把长期凭证写进 URL，见 PROJECT_OVERVIEW 九）。
     * <p>
     * nosniff + Content-Type 只能取白名单里的 5 种图片 MIME：即使有人把 HTML 改名成 .png 传上来，
     * 浏览器也不会把它当页面执行。
     */
    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id, CurrentUser user) {
        Attachment attachment = attachmentService.requireOwned(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMime()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getData());
    }
}
