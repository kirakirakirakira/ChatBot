<script setup lang="ts">
import type { UiMessage } from '@/types'

defineProps<{
  message: UiMessage
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

      <div v-if="message.content" class="content">{{ message.content }}<span
        v-if="message.streaming"
        class="cursor"
      >▍</span></div>
      <div v-else-if="message.streaming && !message.reasoning" class="content placeholder">等待模型响应…</div>

      <div v-if="message.error" class="error">⚠ {{ message.error }}</div>
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

.cursor {
  animation: blink 1s step-start infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
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
</style>