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
  <div class="bubble-row" :class="message.role">
    <div class="bubble">
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
      <div v-else-if="message.content" class="content">{{ message.content }}</div>
      <div v-else-if="message.streaming && !message.reasoning" class="content placeholder">等待模型响应…</div>

      <div v-if="message.error" class="error">⚠ {{ message.error }}</div>

      <!-- 用量行：这条回答是哪个模型、烧了多少 token。思考 token 单列，
           因为「思考 2.4 万字、回答 1 个字」这种成本事故必须一眼看得见 -->
      <div
        v-if="message.role === 'assistant' && (message.model || message.promptTokens != null)"
        class="meta"
      >
        <span v-if="message.model">{{ message.model }}</span>
        <span v-if="message.promptTokens != null && message.completionTokens != null">
          · 输入 {{ message.promptTokens }} / 输出 {{ message.completionTokens }} tokens<span
            v-if="message.reasoningTokens != null"
          >（含思考 {{ message.reasoningTokens }}）</span>
        </span>
      </div>

      <div v-if="canRegenerate" class="actions">
        <button
          class="regen"
          type="button"
          title="删掉这条回答，用同一条问题重新生成"
          @click="emit('regenerate')"
        >重新生成</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bubble-row {
  display: flex;
}

.bubble-row.user {
  justify-content: flex-end;
}

.bubble-row.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: min(760px, 85%);
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 15px;
  line-height: 1.6;
}

.user .bubble {
  background: var(--user-bubble);
  color: var(--user-bubble-text);
  border-bottom-right-radius: 4px;
}

.assistant .bubble {
  background: var(--panel);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.content {
  white-space: pre-wrap;
  word-break: break-word;
}

.placeholder {
  color: var(--text-muted);
}

.reasoning {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-muted);
}

.reasoning summary {
  cursor: pointer;
  user-select: none;
}

.reasoning-content {
  margin-top: 6px;
  padding: 8px 10px;
  max-height: 240px;
  overflow-y: auto;
  border-radius: 8px;
  background: var(--bg);
  white-space: pre-wrap;
  word-break: break-word;
}

.error {
  margin-top: 6px;
  font-size: 13px;
  color: var(--danger);
}

.meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.actions {
  margin-top: 6px;
}

/* 刻意做成弱化的文字按钮：重新生成是低频操作，不该和正文抢注意力 */
.regen {
  padding: 2px 0;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
}

.regen:hover {
  color: var(--accent);
}
</style>