<script setup lang="ts">
import type { UiMessage } from '@/types'
import MarkdownContent from '@/components/MarkdownContent.vue'

defineProps<{
  message: UiMessage
  /** 是否显示「重新生成」：只有最后一条助手消息、且不在生成中时才给。 */
  canRegenerate?: boolean
}>()

const emit = defineEmits<{
  regenerate: []
}>()
</script>

<template>
  <div class="row" :class="message.role">
    <!-- 助手消息带头像、不用气泡底：长回答铺在背景上比塞进盒子里好读（ChatGPT / Claude 现行做法） -->
    <div v-if="message.role === 'assistant'" class="avatar" aria-hidden="true">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3" /><path d="M18.4 5.6 16.3 7.7" /><path d="M21 12h-3" /><path d="M5.6 7.7 7.7 5.6" /><path d="M3 12h3" /><path d="M12 21a9 9 0 0 0 9-9H3a9 9 0 0 0 9 9Z" /></svg>
    </div>

    <div class="body">
      <!-- 思考过程：生成中默认展开，结束后收起，可手动再展开。
           :open 绑成 undefined 而不是 false：open="false" 这个属性只要存在就生效。 -->
      <details v-if="message.reasoning" class="reasoning" :open="message.streaming || undefined">
        <summary>{{ message.streaming && !message.content ? '思考中…' : '思考过程' }}</summary>
        <div class="reasoning-content">{{ message.reasoning }}</div>
      </details>

      <!-- 助手消息走 Markdown（代码块 / 列表 / 表格）；用户消息刻意保持纯文本：
           用户输入的是「话」不是文档，把里面的 * _ # 渲染掉只会让人困惑 -->
      <MarkdownContent
        v-if="message.content && message.role === 'assistant'"
        :content="message.content"
        :streaming="message.streaming"
      />
      <div v-else-if="message.content" class="user-text">{{ message.content }}</div>
      <div v-else-if="message.streaming && !message.reasoning" class="placeholder">等待模型响应…</div>

      <div v-if="message.error" class="error">⚠ {{ message.error }}</div>

      <!-- 用量行：这条回答是哪个模型、烧了多少 token。思考 token 单列，
           因为「思考 2.4 万字、回答 1 个字」这种成本事故必须一眼看得见 -->
      <div
        v-if="message.role === 'assistant' && (message.model || message.promptTokens != null)"
        class="foot"
      >
        <span v-if="message.model" class="meta">{{ message.model }}</span>
        <span v-if="message.promptTokens != null && message.completionTokens != null" class="meta">
          输入 {{ message.promptTokens }} / 输出 {{ message.completionTokens }} tokens<span
            v-if="message.reasoningTokens != null"
          >（含思考 {{ message.reasoningTokens }}）</span>
        </span>
        <button
          v-if="canRegenerate"
          class="regen"
          type="button"
          title="删掉这条回答，用同一条问题重新生成"
          @click="emit('regenerate')"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 15-6.7L21 8" /><path d="M21 3v5h-5" /><path d="M21 12a9 9 0 0 1-15 6.7L3 16" /><path d="M3 21v-5h5" /></svg>
          重新生成
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  gap: 12px;
  animation: row-in 180ms ease;
}

@keyframes row-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
}

.row.user {
  justify-content: flex-end;
}

.avatar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-top: 2px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 50%, #2b6cb0));
  color: #fff;
}

.body {
  min-width: 0;
  max-width: min(720px, 100%);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.row.user .body {
  align-items: flex-end;
}

/* 用户消息是「说的话」：一个浅色圆角块就够，不用头像不用 Markdown */
.user-text {
  padding: 10px 14px;
  border-radius: 18px 18px 4px 18px;
  background: color-mix(in srgb, var(--accent) 10%, var(--panel));
  border: 1px solid color-mix(in srgb, var(--accent) 22%, transparent);
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}

.placeholder {
  color: var(--text-muted);
  font-size: 14px;
}

.reasoning {
  font-size: 13px;
  color: var(--text-muted);
  border-left: 2px solid color-mix(in srgb, var(--accent) 45%, transparent);
  padding-left: 10px;
}

.reasoning summary {
  cursor: pointer;
  user-select: none;
  width: fit-content;
  border-radius: 6px;
  padding: 1px 4px;
}

.reasoning summary:hover {
  background: color-mix(in srgb, var(--text) 6%, transparent);
  color: var(--text);
}

.reasoning-content {
  margin-top: 6px;
  padding: 8px 10px;
  max-height: 240px;
  overflow-y: auto;
  border-radius: var(--radius-s);
  background: color-mix(in srgb, var(--text) 4%, transparent);
  white-space: pre-wrap;
  word-break: break-word;
}

.error {
  padding: 6px 10px;
  border-radius: var(--radius-s);
  font-size: 13px;
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 9%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger) 22%, transparent);
  width: fit-content;
}

.foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.meta {
  font-size: 11.5px;
  color: var(--text-muted);
  opacity: 0.85;
}

/* 重新生成是低频操作：默认半透明，悬停消息时才显形，不和正文抢注意力 */
.regen {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 120ms ease, background 120ms ease, color 120ms ease;
}

.row:hover .regen,
.regen:focus-visible {
  opacity: 1;
}

.regen:hover {
  background: color-mix(in srgb, var(--text) 8%, transparent);
  color: var(--text);
}
</style>
