<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { clearMemories, createMemory, deleteMemory, fetchMemories, updateMemory } from '@/api/memory'
import type { MemoryKind, UserMemory } from '@/types'

/**
 * AI 记忆管理弹窗：列表 + 新增 / 编辑 / 删除 / 清空。
 *
 * 开关不在这里：个人信息页已经有总开关，重复一份会多一个「到底哪个说了算」的问题。
 * 每次写操作成功后 emit('changed')，让父页面重新拉一次计数。
 */
const emit = defineEmits<{
  close: []
  changed: []
}>()

const memories = ref<UserMemory[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')
const saving = ref(false)
const deleting = ref(false)
const clearing = ref(false)

const editingId = ref<number | null>(null)
const draftKind = ref<MemoryKind>('PREFERENCE')
const draftContent = ref('')

const deleteTarget = ref<UserMemory | null>(null)
const showClearConfirm = ref(false)

const kindOptions: { value: MemoryKind; label: string }[] = [
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'FACT', label: '事实' },
  { value: 'PROJECT', label: '项目' },
  { value: 'CONSTRAINT', label: '约束' },
]

const isEditing = computed(() => editingId.value !== null)
const canSubmit = computed(() => draftContent.value.trim().length > 0 && !saving.value)
const deleteText = computed(() => deleteTarget.value
  ? `删除后不可恢复：\n${deleteTarget.value.content}`
  : '')

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    memories.value = await fetchMemories()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = null
  draftKind.value = 'PREFERENCE'
  draftContent.value = ''
}

function startAdd(): void {
  resetForm()
  error.value = ''
}

function startEdit(memory: UserMemory): void {
  editingId.value = memory.id
  draftKind.value = memory.kind
  draftContent.value = memory.content
  error.value = ''
  success.value = ''
}

async function save(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const wasEditing = editingId.value !== null
    const body = { kind: draftKind.value, content: draftContent.value.trim() }
    if (editingId.value === null) {
      await createMemory(body)
    } else {
      await updateMemory(editingId.value, body)
    }
    resetForm()
    success.value = wasEditing ? '已保存' : '已添加'
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

async function confirmDelete(): Promise<void> {
  const target = deleteTarget.value
  if (target === null || deleting.value) {
    return
  }
  deleting.value = true
  error.value = ''
  try {
    await deleteMemory(target.id)
    deleteTarget.value = null
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    deleting.value = false
  }
}

async function confirmClear(): Promise<void> {
  if (clearing.value) {
    return
  }
  clearing.value = true
  error.value = ''
  try {
    await clearMemories()
    showClearConfirm.value = false
    resetForm()
    await load()
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    clearing.value = false
  }
}

function formatTime(iso: string | null): string {
  if (!iso) {
    return '还没用过'
  }
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return '—'
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-card memory-modal">
      <div class="memory-head">
        <h2 class="modal-title">AI 记忆</h2>
        <button class="btn-ghost close-btn" type="button" @click="emit('close')">关闭</button>
      </div>

      <div v-if="error" class="alert-error">{{ error }}</div>
      <div v-else-if="success" class="alert-ok">{{ success }}</div>

      <div class="memory-form">
        <div class="form-row">
          <label class="form-label">
            <span class="field-label">类型</span>
            <select v-model="draftKind" class="field-input" :disabled="saving">
              <option v-for="opt in kindOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </label>
        </div>
        <textarea
          v-model="draftContent"
          class="field-input memory-input"
          rows="3"
          maxlength="500"
          :disabled="saving"
        ></textarea>
        <div class="form-foot">
          <span class="counter">{{ draftContent.length }} / 500</span>
          <div class="form-actions">
            <button v-if="isEditing" class="btn-ghost" type="button" :disabled="saving" @click="startAdd">取消编辑</button>
            <button class="btn-primary" type="button" :disabled="!canSubmit" @click="save">
              {{ saving ? '保存中…' : (isEditing ? '保存修改' : '添加记忆') }}
            </button>
          </div>
        </div>
      </div>

      <div class="memory-list">
        <div v-if="loading" class="empty-state">加载中…</div>
        <div v-else-if="memories.length === 0" class="empty-state">
          还没有记忆。
        </div>
        <template v-else>
          <article v-for="memory in memories" :key="memory.id" class="memory-row">
            <div class="memory-meta">
              <span class="kind-chip">{{ memory.kindLabel }}</span>
              <span class="memory-source">{{ memory.sourceLabel }}</span>
              <span class="memory-time" :title="'最近使用：' + formatTime(memory.lastUsedAt)">
                更新于 {{ formatTime(memory.updatedAt) }}
              </span>
            </div>
            <p class="memory-content">{{ memory.content }}</p>
            <div class="memory-actions">
              <button class="link-btn" type="button" :disabled="saving" @click="startEdit(memory)">编辑</button>
              <button class="link-btn danger-text" type="button" :disabled="deleting" @click="deleteTarget = memory">删除</button>
            </div>
          </article>
        </template>
      </div>

      <div class="modal-actions memory-foot">
        <button class="link-btn danger-text" type="button" :disabled="memories.length === 0 || clearing" @click="showClearConfirm = true">
          清空全部
        </button>
        <button class="btn-primary" type="button" @click="emit('close')">完成</button>
      </div>
    </div>

    <ConfirmDialog
      v-if="deleteTarget"
      title="删除记忆"
      :text="deleteText"
      confirm-label="删除"
      :busy="deleting"
      danger
      @close="deleteTarget = null"
      @confirm="confirmDelete"
    />
    <ConfirmDialog
      v-if="showClearConfirm"
      title="清空所有记忆"
      text="将删除全部 AI 记忆，且不可恢复。"
      confirm-label="清空全部"
      :busy="clearing"
      danger
      @close="showClearConfirm = false"
      @confirm="confirmClear"
    />
  </div>
</template>

<style scoped>
.memory-modal {
  max-width: 720px;
  max-height: 88vh;
  gap: 12px;
}

.memory-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.close-btn {
  flex: none;
  padding: 7px 12px;
}

.memory-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: var(--panel-2);
}

.form-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.form-label {
  width: 150px;
}

.counter,
.memory-source,
.memory-time {
  font-size: 12px;
  color: var(--text-muted);
}

.memory-input {
  resize: vertical;
  min-height: 68px;
  font-family: inherit;
  line-height: 1.6;
}

.form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.form-actions {
  display: flex;
  gap: 8px;
}

.memory-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 120px;
  max-height: 42vh;
  overflow-y: auto;
  padding-right: 2px;
}

.empty-state {
  padding: 26px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

.memory-row {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: var(--panel);
}

.memory-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.kind-chip {
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11.5px;
  font-weight: 600;
}

.memory-time {
  margin-left: auto;
}

.memory-content {
  margin: 8px 0;
  font-size: 13.5px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.memory-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
}

.link-btn {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--accent);
  font-size: 12.5px;
  cursor: pointer;
}

.link-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.danger-text {
  color: var(--danger);
}

.memory-foot {
  align-items: center;
  justify-content: space-between;
}
</style>

