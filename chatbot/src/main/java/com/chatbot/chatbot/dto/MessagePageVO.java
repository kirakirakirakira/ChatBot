package com.chatbot.chatbot.dto;

import java.util.List;

/**
 * 消息分页响应（{@code GET /api/conversations/{id}/messages}）。
 * <p>
 * 刻意不返回总条数和总页数：聊天界面只需要「还能不能往前翻」，
 * 而算总数要在 LONGTEXT 大表上多跑一次 count(*)，每翻一页都跑一次不值。
 *
 * @param items    本页消息，<b>按 id 升序（时间正序）</b>，前端直接渲染，不用再反转
 * @param beforeId 加载更早一页时要带的游标（本页最老一条的 id）；没有更早的了就是 null
 * @param hasMore  是否还有更早的消息；false 时前端不该再显示「加载更早的消息」
 */
public record MessagePageVO(List<MessageVO> items, Long beforeId, boolean hasMore) {
}
