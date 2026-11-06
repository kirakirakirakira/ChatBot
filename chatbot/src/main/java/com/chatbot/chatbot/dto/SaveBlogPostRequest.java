package com.chatbot.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 编辑博客文章的请求体。
 * <p>
 * summary 可空：空着就由后端从正文提取摘要，避免作者为了「能发出去」被迫再写一遍导语。
 * content 上限 50000 字符：LONGTEXT 能装更多，但一篇正常博客远到不了这个量；
 * 设上限是为了挡住「把整个文件粘进来」的误操作，也让列表摘要和渲染有可预期的成本。
 */
public record SaveBlogPostRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题最多 100 个字符")
        String title,

        @Size(max = 200, message = "摘要最多 200 个字符")
        String summary,

        @NotBlank(message = "正文不能为空")
        @Size(max = 50000, message = "正文最多 50000 个字符")
        String content) {
}