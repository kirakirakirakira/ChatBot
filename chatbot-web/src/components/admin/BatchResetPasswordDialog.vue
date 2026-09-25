<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { batchAdminUsers } from '@/api/userAdmin'
import type { AdminUser, BatchUserResult } from '@/types'

/**
 * 批量重置密码。和单人的 ResetPasswordDialog 是同一个交互（随机生成 / 手输），只是作用于一批人。
 *
 * 两种模式的差别比单人版更要紧：
 * - **随机生成**：每人一个不同的 16 位密码，明文只在结果弹窗里回显一次（后端不存明文）。
 *   批量场景下这是唯一 sane 的选择——手输一个密码发给 20 个人，等于让 20 个人共用一个凭据。
 * - **手输**：所有人设成同一个密码。界面上直接把这句话写出来，别让人以为「只会改第一个」。
 *
 * 无论哪种模式，这些人的旧登录态都立刻失效，且下次登录被强制改密。
 */
const props = defineProps<{ users: AdminUser[] }>()

const emit = defineEmits<{
  close: []
  /** 交给父组件弹结果窗：逐条成功 / 失败原因、以及每人的新密码都在里面。 */
  done: [result: BatchUserResult]
}>()

const mode = ref<'generate' | 'input'>('generate')
const newPassword = ref('')
const submitting = ref(false)
const error = ref('')

const names = computed(() => props.users.map((u) => u.username))
/** 名字多了只显示前几个：弹窗宽度有限，全列出来会把「确认」按钮挤到屏幕外。 */
const namesText = computed(() => {
  const list = names.value
  if (list.length <= 4) {
    return list.join('、')
  }
  return list.slice(0, 4).join('、') + ' 等 ' + String(list.length) + ' 人'
})

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
    const result = await batchAdminUsers({
      ids: props.users.map((u) => u.id),
      action: 'RESET_PASSWORD',
      ...(mode.value === 'generate' ? { generate: true } : { newPassword: newPassword.value }),
    })
    emit('done', result)
    emit('close')
  } catch (e) {
    // 整批级的错误（密码太短 / 超过 100 个）才会走到这里；逐条失败在结果窗里看
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="submitting ? undefined : emit('close')">
    <div class="modal-card">
      <h2 class="modal-title">批量重置密码<span class="modal-title-note">{{ users.length }} 人</span></h2>

      <p class="modal-tip">对象：{{ namesText }}</p>

      <div v-if="error" class="alert-error">{{ error }}</div>

      <div class="mode-row">
        <label class="mode-item">
          <input v-model="mode" type="radio" value="generate" :disabled="submitting" />
          每人一个随机密码
        </label>
        <label class="mode-item">
          <input v-model="mode" type="radio" value="input" :disabled="submitting" />
          统一设成一个密码
        </label>
      </div>

      <label v-if="mode === 'input'" class="field">
        <span class="field-label">新密码</span>
        <input
          v-model="newPassword"
          class="field-input"
          type="text"
          autocomplete="off"
          placeholder="6~64 位，所有人共用"
          :disabled="submitting"
          autofocus
        />
      </label>
      <p v-else class="modal-tip">
        由后端用 SecureRandom 生成，每人 16 位且互不相同；明文只在结果窗里出现一次，请当场抄走或复制。
      </p>

      <p class="modal-tip">
        这些账号的现有登录态会立刻失效，且下次登录被强制改密码。
      </p>

      <div class="modal-actions">
        <button class="btn-ghost" type="button" :disabled="submitting" @click="emit('close')">取消</button>
        <button
          class="btn-primary"
          type="button"
          :disabled="submitting || (mode === 'input' && !newPassword)"
          @click="submit"
        >
          {{ submitting ? '重置中…' : '重置 ' + users.length + ' 人的密码' }}
        </button>
      </div>
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
}

.mode-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  cursor: pointer;
}

.modal-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}
</style>
