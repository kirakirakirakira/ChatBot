<script setup lang="ts">
import { ref } from 'vue'
import type { CurrentUser } from '@/types'
import { updateSystemPrompt } from '@/api'

const props = defineProps<{
  /** 打开弹窗时本地登录态里的人设，避免为了回显再请求一次 /me。 */
  initial: string
}>()

const emit = defineEmits<{
  close: []
  changed: [user: CurrentUser]
}>()

const draft = ref(props.initial)
const saving = ref(false)
const error = ref('')

/** 保存后父组件用返回的用户信息覆盖本地登录态，不用重新登录、也不用重拉 /me。 */
async function save(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  error.value = ''
  try {
    const user = await updateSystemPrompt(draft.value)
    emit('changed', user)
    emit('close')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    emit('close')
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')" @keydown="onKeydown">
    <div class="modal-card wide">
      <h2 class="modal-title">系统提示词</h2>
      <p class="hint-text">
        每轮对话都会把这段话作为 system 消息放在历史最前面，所有会话共用。清空后保存 = 移除人设。
      </p>
      <textarea
        v-model="draft"
        class="field-input prompt-input"
        rows="8"
        maxlength="2000"
        placeholder="例如：你是一名资深 DBA，只回答数据库相关问题，输出尽量用表格"
      ></textarea>
      <div class="prompt-foot">
        <span class="counter">{{ draft.length }} / 2000</span>
        <div v-if="error" class="alert-error">{{ error }}</div>
      </div>
      <div class="modal-actions">
        <button class="btn-ghost" type="button" :disabled="saving" @click="emit('close')">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hint-text {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--text-muted);
}

.prompt-input {
  width: 100%;
  resize: vertical;
  font-family: inherit;
  line-height: 1.6;
}

.prompt-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 4px;
}

.counter {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
