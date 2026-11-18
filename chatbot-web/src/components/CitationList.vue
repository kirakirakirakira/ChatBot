<script setup lang="ts">
import type { Citation } from '@/types'
import { formatDateTime } from '@/lib/format'

/**
 * 引用卡列表。流式期间由 AgentTimeline 渲染（数据来自 tool_result.ui），
 * 刷新 / 切会话后由 MessageBubble 渲染（数据来自 GET messages 的 citations）。
 * 两处共用这一个组件，保证形状和样式不会漂。
 */
defineProps<{
  citations: Citation[]
}>()

function citationKind(role: Citation['role']): string {
  if (role === 'user') {
    return '你的提问'
  }
  return role === 'assistant' ? '回答' : '整个会话'
}

/** 历史消息读回来的引用没有 key（后端只返回业务字段），这里拼一个稳定 key。 */
function keyOf(citation: Citation): string {
  return citation.key ?? `${citation.conversationId}-${citation.messageId ?? 'all'}`
}
</script>

<template>
  <div v-if="citations.length > 0" class="citations">
    <RouterLink
      v-for="c in citations"
      :key="keyOf(c)"
      class="citation"
      :to="c.url"
    >
      <span class="citation-top">
        <span class="citation-title">{{ c.conversationTitle }}</span>
        <span class="citation-meta">{{ citationKind(c.role) }} · {{ formatDateTime(c.createdAt) }}</span>
      </span>
      <span v-if="c.snippet" class="citation-snippet">{{ c.snippet }}</span>
    </RouterLink>
  </div>
</template>

<style scoped>
.citations {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 7px;
}

.citation {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid color-mix(in srgb, var(--border) 80%, transparent);
  border-radius: var(--radius-s);
  background: var(--panel);
  color: inherit;
  text-decoration: none;
  transition: border-color 120ms ease, background 120ms ease;
}

.citation:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: color-mix(in srgb, var(--accent) 6%, var(--panel));
}

.citation-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.citation-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
  font-size: 12px;
}

.citation-meta {
  flex: none;
  font-size: 11px;
  color: var(--text-muted);
}

.citation-snippet {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
