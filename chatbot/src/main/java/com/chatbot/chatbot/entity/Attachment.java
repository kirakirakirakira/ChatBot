package com.chatbot.chatbot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 用户上传的图片附件（当前唯一用途：随消息发给多模态模型）。
 * <p>
 * 字节直接存 MySQL LONGBLOB 而不是落磁盘 / 对象存储：单机部署少一个「文件跑哪去了」的运维面，
 * 代价是数据库变大、备份变慢。图片限 5MB、每条消息限 4 张，量级可控。
 * 真要上生产多实例，把 data 换成对象存储 key 即可，其余代码不用动。
 * <p>
 * 归属不存 owner_id：和 message 一样通过 conversation 传递，越权校验在会话层做一次就够。
 */
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * 关联到的那条<b>用户消息</b> id；NULL = 传上来了但还没随消息发出去（孤儿）。
     * <p>
     * 刻意不做 {@code @ManyToOne Message}：附件在消息之前就存在（先上传拿 id，再发消息），
     * 做成可空关联既绕不出这个先后顺序，还会让删消息时多一层级联要考虑。
     * 一个附件只会被链到一条消息上（link 时带 message_id IS NULL 条件），历史拼装才不会重复带图。
     */
    @Column(name = "message_id")
    private Long messageId;

    /** 白名单里的图片 MIME，取值见 AttachmentService.ALLOWED_MIME；出网时直接当 Content-Type 用。 */
    @Column(nullable = false, length = 64)
    private String mime;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** 原始字节数。列名带 _bytes：JPQL 里 size 是保留函数名，叫 size 会让查询解析出歧义。 */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
