<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { resetAdminUserPassword } from '@/api/userAdmin'
import type { AdminUser } from '@/types'

/**
 * 管理员重置某个用户的密码。
 *
 * 两种模式：手输新密码 / 一键生成随机密码。生成模式下密码**只在响应里回显这一次**，
 * 后端不存明文也不存可逆形式，关掉弹窗就再也看不到了——所以这里给复制按钮和醒目提示。
 * 无论哪种模式，目标用户的旧登录态都会立刻失效，且本人下次登录被强制改密。
 */
const props = defineProps<{ user: AdminUser }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const mode = ref<'generate' | 'input'>('generate')
const newPassword = ref('')
const submitting = ref(false)
const error = ref('')
/** 非空 = 重置已完成且是生成模式：界面切到「展示一次性密码」。 */
const generated = ref('')
const copied = ref(false)

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape' && !submitting.value) {
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const result = await resetAdminUserPassword(props.user.id, mode.value === 'generate' ? { generate: true } : { newPassword: newPassword.value })
    emit('changed')
    if (result.generatedPassword) {
      generated.value = result.generatedPassword
    } else {
      emit('close')
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    submitting.value = false
  }
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(generated.value)
    copied.value = true
  } catch {
    error.value = '复制失败，请手动选中复制'
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="submitting ? undefined : emit('close')">
    <div class="modal-card">
      <h2 class="modal-title">重置密码<span class="modal-title-note">{{ user.username }}</span></h2>

      <!-- 已完成：一次性密码展示 -->
      <template v-if="generated">
        <div class="alert-ok">密码已重置，该用户的所有登录态已失效，下次登录会被要求改密码。</div>
        <label class="field">
          <span class="field-label">新密码（只显示这一次）</span>
          <input class="field-input generated" type="text" :value="generated" readonly autofocus @focus="($event.target as HTMLInputElement).select()" />
        </label>
        <div v-if="error" class="alert-error">{{ error }}</div>
        <div class="modal-actions">
          <button class="btn-ghost" type="button" @click="copy">{{ copied ? '已复制' : '复制' }}</button>
          <button class="btn-primary" type="button" @click="emit('close')">完成</button>
        </div>
      </template>

      <!-- 表单 -->
      <template v-else>
        <div v-if="error" class="alert-error">{{ error }}</div>

        <div class="mode-row">
          <label class="mode-item">
            <input v-model="mode" type="radio" value="generate" :disabled="submitting" />
            生成随机密码
          </label>
          <label class="mode-item">
            <input v-model="mode" type="radio" value="input" :disabled="submitting" />
            手动指定
          </label>
        </div>

        <label v-if="mode === 'input'" class="field">
          <span class="field-label">新密码</span>
          <input v-model="newPassword" class="field-input" type="text" autocomplete="off" placeholder="6~64 位" :disabled="submitting" />
        </label>

        <p class="modal-tip">重置后该用户在所有设备上的登录态立即失效，且下次登录必须改密码。把新密码通过安全渠道交给对方。</p>

        <div class="modal-actions">
          <button class="btn-ghost" type="button" :disabled="submitting" @click="emit('close')">取消</button>
          <button class="btn-primary" type="button" :disabled="submitting || (mode === 'input' && !newPassword)" @click="submit">
            {{ submitting ? '重置中…' : '重置密码' }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.modal-title-note {
  margin-left: 8px;
  font-size: 12px;
  font-weight: normal;
  color: var(--text-muted);
}

.mode-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.mode-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  cursor: pointer;
}

.generated {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.4px;
}

.modal-tip {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}
</style>
