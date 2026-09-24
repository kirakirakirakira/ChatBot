<script setup lang="ts">
import { nextTick, ref } from 'vue'
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
  rename: [id: number, title: string]
}>()

/** 正在行内改名的会话 id；同一时刻只允许一个，null = 没在改。 */
const editingId = ref<number | null>(null)
const draft = ref('')
const renameInput = ref<HTMLInputElement | null>(null)

/** v-for 里的 ref 会编译成数组，用函数 ref 只收当前这一个输入框。 */
function setRenameInput(el: unknown): void {
  renameInput.value = el instanceof HTMLInputElement ? el : null
}

function startRename(id: number, title: string): void {
  editingId.value = id
  draft.value = title
  void nextTick(() => {
    renameInput.value?.focus()
    renameInput.value?.select()
  })
}

/** Enter 或失焦提交。先清 editingId：输入框随之卸载，blur 再触发时这里的守卫会挡掉第二次提交。 */
function commitRename(id: number): void {
  if (editingId.value !== id) {
    return
  }
  editingId.value = null
  const title = draft.value.trim()
  if (!title) {
    return // 空标题等于没改，静默取消；后端 @NotBlank 也兜着
  }
  emit('rename', id, title)
}

/** Esc 取消：输入框直接卸载，不会走 blur 提交。 */
function cancelRename(): void {
  editingId.value = null
}

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
    <div class="brand">
      <span class="brand-mark" aria-hidden="true">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3" /><path d="M18.4 5.6 16.3 7.7" /><path d="M21 12h-3" /><path d="M5.6 7.7 7.7 5.6" /><path d="M3 12h3" /><path d="M12 21a9 9 0 0 0 9-9H3a9 9 0 0 0 9 9Z" /></svg>
      </span>
      <span class="brand-name">Chatbot</span>
    </div>

    <button
      class="new-chat"
      type="button"
      :disabled="!canCreate"
      :title="canCreate ? '新建对话' : '当前对话还没有消息，直接发消息即可'"
      @click="emit('create')"
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14" /><path d="M5 12h14" /></svg>
      新建对话
    </button>

    <ul class="conv-list">
      <li
        v-for="c in conversations"
        :key="c.id"
        class="conv-item"
        :class="{ active: c.id === activeId }"
        @click="emit('select', c.id)"
      >
        <input
          v-if="editingId === c.id"
          :ref="setRenameInput"
          v-model="draft"
          class="conv-rename"
          maxlength="100"
          @click.stop
          @keydown.enter="commitRename(c.id)"
          @keydown.esc="cancelRename()"
          @blur="commitRename(c.id)"
        />
        <span
          v-else
          class="conv-title"
          :title="c.title + '（双击重命名）'"
          @dblclick.stop="startRename(c.id, c.title)"
        >{{ c.title }}</span>

        <span class="conv-side">
          <span class="conv-time">{{ formatTime(c.updatedAt) }}</span>
          <span class="conv-actions">
            <button
              class="icon-btn"
              type="button"
              title="重命名"
              @click.stop="startRename(c.id, c.title)"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /></svg>
            </button>
            <button
              class="icon-btn danger"
              type="button"
              title="删除对话"
              @click.stop="emit('remove', c.id)"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></svg>
            </button>
          </span>
        </span>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 264px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 12px;
  background: var(--panel-2);
  border-right: 1px solid var(--border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 2px 6px 6px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 50%, #2b6cb0));
  color: #fff;
  box-shadow: var(--shadow-1);
}

.brand-name {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  padding: 9px 12px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-m);
  background: transparent;
  color: var(--text);
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease, color 120ms ease;
}

.new-chat:not(:disabled):hover {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  border-style: solid;
  color: var(--accent-strong);
}

.new-chat:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.conv-list {
  flex: 1;
  margin: 0;
  padding: 2px 0;
  list-style: none;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conv-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: var(--radius-m);
  cursor: pointer;
  transition: background 120ms ease;
}

.conv-item:hover {
  background: color-mix(in srgb, var(--text) 6%, transparent);
}

.conv-item.active {
  background: var(--accent-soft);
}

.conv-item.active .conv-title {
  color: var(--accent-strong);
  font-weight: 600;
}

.conv-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
}

.conv-side {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.conv-time {
  font-size: 11px;
  color: var(--text-muted);
  transition: opacity 100ms ease;
}

/* 操作按钮悬停时才出现，并盖住时间：一行宽度有限，两者同时显示会挤 */
.conv-actions {
  position: absolute;
  right: -4px;
  display: flex;
  gap: 2px;
  padding-left: 10px;
  background: linear-gradient(to right, transparent, var(--panel-2) 30%);
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms ease;
}

.conv-item:hover .conv-actions {
  opacity: 1;
  pointer-events: auto;
}

.conv-item:hover .conv-time {
  opacity: 0;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.icon-btn:hover {
  background: color-mix(in srgb, var(--text) 10%, transparent);
  color: var(--text);
}

.icon-btn.danger:hover {
  background: color-mix(in srgb, var(--danger) 14%, transparent);
  color: var(--danger);
}

.conv-rename {
  flex: 1;
  min-width: 0;
  padding: 3px 7px;
  border: 1px solid var(--accent);
  border-radius: 6px;
  background: var(--panel);
  color: var(--text);
  font-size: 13.5px;
  outline: none;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 18%, transparent);
}
</style>
