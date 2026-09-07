<script setup lang="ts">
import type { Conversation } from '@/types'

defineProps<{
  conversations: Conversation[]
  activeId: number | null
  /** false = 当前对话还是空的，禁止再新建（防止重复添加空对话）。 */
  canCreate: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  create: []
  remove: [id: number]
}>()

/** 今天显示 HH:mm，更早显示 MM-DD。 */
function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return ''
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  return sameDay ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
</script>

<template>
  <aside class="sidebar">
    <button
      class="new-chat"
      type="button"
      :disabled="!canCreate"
      :title="canCreate ? '新建对话' : '当前对话还没有消息，直接发消息即可'"
      @click="emit('create')"
    >＋ 新建对话</button>
    <ul class="conv-list">
      <li
        v-for="c in conversations"
        :key="c.id"
        class="conv-item"
        :class="{ active: c.id === activeId }"
        @click="emit('select', c.id)"
      >
        <span class="conv-title" :title="c.title">{{ c.title }}</span>
        <span class="conv-time">{{ formatTime(c.updatedAt) }}</span>
        <button
          class="conv-delete"
          type="button"
          title="删除对话"
          @click.stop="emit('remove', c.id)"
        >×</button>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: var(--panel);
  border-right: 1px solid var(--border);
}

.new-chat {
  width: 100%;
  padding: 10px;
  border: none;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
}

.new-chat:not(:disabled):hover {
  filter: brightness(1.08);
}

.new-chat:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.conv-list {
  flex: 1;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 30px 10px 10px;
  border-radius: 8px;
  cursor: pointer;
}

.conv-item:hover {
  background: var(--bg);
}

.conv-item.active {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
}

.conv-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.conv-time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-muted);
}

.conv-delete {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  visibility: hidden;
}

.conv-item:hover .conv-delete {
  visibility: visible;
}

.conv-delete:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 12%, transparent);
}
</style>