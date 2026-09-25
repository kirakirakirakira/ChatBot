<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { createAdminUser } from '@/api/userAdmin'
import type { AdminUser, UserAdminOptions } from '@/types'

const props = defineProps<{ options: UserAdminOptions }>()
const emit = defineEmits<{ close: []; created: [user: AdminUser] }>()

const username = ref('')
const password = ref('')
const role = ref(0)
const status = ref(0)
const submitting = ref(false)
const error = ref('')

/**
 * 生成一个看得清、打得对的随机密码：去掉 0/O/1/l/I 这类易混字符。
 * 用 crypto.getRandomValues 而不是 Math.random——后者不是密码学安全的。
 */
function generatePassword(): void {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
  const bytes = new Uint32Array(16)
  crypto.getRandomValues(bytes)
  password.value = Array.from(bytes, (b) => alphabet[b % alphabet.length] ?? 'a').join('')
}

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
    const created = await createAdminUser({
      username: username.value,
      password: password.value,
      role: role.value,
      status: status.value,
    })
    emit('created', created)
  } catch (e) {
    // 后端文案已经是中文（用户名已存在 / 长度不符…），直接展示
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="submitting ? undefined : emit('close')">
    <form class="modal-card" @submit.prevent="submit">
      <h2 class="modal-title">新建用户</h2>

      <div v-if="error" class="alert-error">{{ error }}</div>

      <label class="field">
        <span class="field-label">用户名</span>
        <input v-model="username" class="field-input" type="text" autocomplete="off" placeholder="1~50 字" :disabled="submitting" autofocus />
      </label>

      <label class="field">
        <span class="field-label">初始密码</span>
        <input v-model="password" class="field-input" type="text" autocomplete="off" placeholder="6~64 位" :disabled="submitting" />
      </label>
      <button class="btn-ghost form-hint-btn" type="button" :disabled="submitting" @click="generatePassword">生成随机密码</button>

      <div class="field-row">
        <label class="field">
          <span class="field-label">角色</span>
          <select v-model.number="role" class="field-input" :disabled="submitting">
            <option v-for="r in props.options.roles" :key="r.code" :value="r.code">{{ r.label }}</option>
          </select>
        </label>
        <label class="field">
          <span class="field-label">状态</span>
          <select v-model.number="status" class="field-input" :disabled="submitting">
            <option v-for="s in props.options.statuses" :key="s.code" :value="s.code">{{ s.label }}</option>
          </select>
        </label>
      </div>

      <p class="modal-tip">创建后对方即可用这个密码登录；之后可以随时在这里重置密码或禁用账号。</p>

      <div class="modal-actions">
        <button class="btn-ghost" type="button" :disabled="submitting" @click="emit('close')">取消</button>
        <button class="btn-primary" type="submit" :disabled="submitting || !username.trim() || !password">创建</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.form-hint-btn {
  align-self: flex-start;
  margin: -6px 0 4px;
  padding: 4px 10px;
  font-size: 12px;
}

.field-row {
  display: flex;
  gap: 12px;
}

.field-row .field {
  flex: 1;
}

.modal-tip {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}
</style>
