package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.AttachmentVO;
import com.chatbot.chatbot.entity.Attachment;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.repository.AttachmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图片附件的上传、校验、挂载与读取。
 * <p>
 * 归属一律沿用会话那一套口径：附件挂在 conversation 上，校验只问「这个会话是不是你的」，
 * 读别人的附件和读别人的会话一样是 404 而不是 403（理由见 ConversationService.requireOwned）。
 */
@Service
public class AttachmentService {

    /**
     * 允许的图片 MIME。同时是「读附件时能写进 Content-Type 的取值集合」——
     * 白名单之外的类型根本存不进来，所以出网那一端不用再担心 text/html 之类的存储型 XSS。
     * 只收常见位图：SVG 能带脚本，刻意不放进来。
     */
    private static final Set<String> ALLOWED_MIME =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp");

    /** 单张上限 5MB。和 spring.servlet.multipart.max-file-size 保持同一个数：这里给的是中文文案，那里给的是 413。 */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /**
     * 一条消息最多带几张图。定得比模型上限（Qwen-VL 系一般 10 张左右）低：
     * 每张图都要 base64 内联进请求体（约放大 1.33 倍），4 张已经把单次请求推到 25MB 量级，
     * 再多是在给超时和计费挖坑，不是在做功能。
     */
    public static final int MAX_PER_MESSAGE = 4;

    private final AttachmentRepository attachmentRepository;
    private final ConversationService conversationService;

    public AttachmentService(AttachmentRepository attachmentRepository, ConversationService conversationService) {
        this.attachmentRepository = attachmentRepository;
        this.conversationService = conversationService;
    }

    /**
     * 上传一张图，返回它的 id + 元信息。此时附件还没有归属消息（message_id 为 NULL），
     * 等用户真的点发送时由 {@link #linkToMessage} 挂到那条用户消息上。
     * <p>
     * 先传后发是刻意的两步：图片上传可能几百毫秒，放在发送流程里会让「点发送」卡住，
     * 而且用户可能传完又删掉——那种情况只是留下一条孤儿附件，随会话删除一起清掉。
     */
    @Transactional
    public AttachmentVO upload(Long conversationId, MultipartFile file, CurrentUser user) {
        Conversation conversation = conversationService.requireOwned(conversationId, user);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不能超过 5MB");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 PNG / JPEG / WebP / GIF / BMP 图片");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取上传文件失败: " + e.getMessage());
        }

        Attachment attachment = new Attachment();
        attachment.setConversation(conversation);
        attachment.setMime(mime.toLowerCase(Locale.ROOT));
        attachment.setFileName(safeFileName(file.getOriginalFilename()));
        attachment.setSizeBytes(bytes.length);
        attachment.setData(bytes);
        return AttachmentVO.from(attachmentRepository.save(attachment));
    }

    /**
     * 校验「这批附件 id 都能挂到这个会话上」，返回去重后的 id。
     * <p>
     * 三件事一起挡：id 不存在、id 属于别人的会话、id 已经被别的消息占用。
     * 都报 400 而不是 404——这里是「你提交的参数不对」，不是「你在找一个资源」，
     * 而且请求体里的 id 是客户端自己攒的，泄露存在性没有额外风险。
     */
    @Transactional(readOnly = true)
    public List<Long> validateForConversation(Long conversationId, Collection<Long> requestedIds) {
        List<Long> ids = dedupe(requestedIds);
        if (ids.isEmpty()) {
            return ids;
        }
        if (ids.size() > MAX_PER_MESSAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "一条消息最多带 " + MAX_PER_MESSAGE + " 张图片");
        }
        Set<Long> linkable = new LinkedHashSet<>(attachmentRepository.findLinkableIds(ids, conversationId));
        List<Long> rejected = ids.stream().filter(id -> !linkable.contains(id)).toList();
        if (!rejected.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "附件不可用（不存在、不属于该会话，或已被别的消息占用）: " + rejected);
        }
        return ids;
    }

    /** 把附件挂到刚存好的用户消息上。调用方需已 validateForConversation，且自带事务。 */
    @Transactional
    public void linkToMessage(Collection<Long> ids, Long messageId, Long conversationId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        attachmentRepository.linkToMessage(ids, messageId, conversationId);
    }

    /** 读字节前的归属校验：不存在或不是自己的，一律 404。 */
    @Transactional(readOnly = true)
    public Attachment requireOwned(Long id, CurrentUser user) {
        return attachmentRepository.findByIdForOwner(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在: " + id));
    }

    /** 原始文件名只用于展示，去掉路径分隔符防止 ../ 之类的东西混进 Content-Disposition。 */
    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.strip();
        if (name.isEmpty()) {
            return "image";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    /** 去掉 null 和重复，保持用户给的顺序（图片顺序会体现在多模态入参里）。 */
    private static List<Long> dedupe(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                unique.add(id);
            }
        }
        return List.copyOf(unique);
    }
}
