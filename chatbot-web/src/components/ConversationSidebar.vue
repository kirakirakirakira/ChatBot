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
        <!-- 双击标题行内改名：Enter / 失焦提交，Esc 取消。
             输入框上 @click.stop，否则点输入框会把整个 li 的 select 也触发掉 -->
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
        <span v-else class="conv-title" :title="c.title + '（双击重命名）'" @dblclick.stop="startRename(c.id, c.title)">{{ c.title }}</span>
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

.conv-rename {
  flex: 1;
  min-width: 0;
  padding: 2px 6px;
  border: 1px solid var(--accent);
  border-radius: 4px;
  background: var(--bg);
  color: var(--text);
  font-size: 14px;
  outline: none;
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