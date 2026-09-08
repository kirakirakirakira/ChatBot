<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { changePassword } from '@/api'
import type { LoginResult } from '@/types'

const emit = defineEmits<{
  close: []
  /**
   * 改成功后把新登录态交回父组件：后端会换发 token（旧 token 因为
   * password_changed_at 已经作废），不换上自己的话下一个请求就 401 了。
   */
  changed: [result: LoginResult]
}>()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const error = ref('')
const ok = ref('')

let closeTimer: ReturnType<typeof setTimeout> | null = null

function close(): void {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = null
  }
  emit('close')
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    close()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (closeTimer) {
    clearTimeout(closeTimer)
  }
})

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  // 前端先拦一道明显的错，省一次往返；真正的校验以后端为准（后端还会查原密码对不对）
  if (newPassword.value.length < 6 || newPassword.value.length > 64) {
    error.value = '新密码长度需在 6~64 之间'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  error.value = ''
  ok.value = ''
  submitting.value = true
  try {
    const result = await changePassword(oldPassword.value, newPassword.value)
    emit('changed', result)
    ok.value = '密码已修改，登录状态已自动续期'
    // 让成功提示露个脸再自动关；组件提前卸载的话 timer 会在 onBeforeUnmount 里清掉
    closeTimer = setTimeout(() => emit('close'), 1200)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="close">
    <form class="modal-card" @submit.prevent="submit">
      <h2 class="modal-title">修改密码</h2>

      <div v-if="error" class="alert-error">{{ error }}</div>
      <div v-if="ok" class="alert-ok">{{ ok }}</div>

      <label class="field">
        <span class="field-label">原密码</span>
        <input
          v-model="oldPassword"
          class="field-input"
          type="password"
          autocomplete="current-password"
          placeholder="当前正在使用的密码"
          :disabled="submitting"
          autofocus
        />
      </label>

      <label class="field">
        <span class="field-label">新密码</span>
        <input
          v-model="newPassword"
          class="field-input"
          type="password"
          autocomplete="new-password"
          placeholder="6~64 位"
          :disabled="submitting"
        />
      </label>

      <label class="field">
        <span class="field-label">确认新密码</span>
        <input
          v-model="confirmPassword"
          class="field-input"
          type="password"
          autocomplete="new-password"
          placeholder="再输入一次新密码"
          :disabled="submitting"
        />
      </label>

      <p class="modal-tip">改完密码后当前设备会自动续期，其他设备上的登录态会立即失效，需要重新登录。</p>

      <div class="modal-actions">
        <button class="btn-ghost" type="button" :disabled="submitting" @click="close">取消</button>
        <button
          class="btn-primary"
          type="submit"
          :disabled="submitting || !oldPassword || !newPassword || !confirmPassword"
        >
          {{ submitting ? '提交中…' : '确认修改' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.modal-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}
</style>