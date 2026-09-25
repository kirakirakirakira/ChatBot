<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 通用二次确认弹窗。危险操作（禁用 / 强制下线 / 删号）都走这里，
 * 不让 window.confirm 那种系统框出现在自己的设计语言里。
 *
 * requireText 非空时，必须输入完全一致的文字才能点确认——
 * 删号这种不可逆操作用它挡「手滑」，比再问一遍「真的吗」有效得多。
 */
const props = withDefaults(
  defineProps<{
    title: string
    text?: string
    confirmLabel?: string
    danger?: boolean
    requireText?: string
    /** 执行中（请求还没回来）：禁用按钮，防连点。 */
    busy?: boolean
  }>(),
  { text: '', confirmLabel: '确认', danger: false, requireText: '', busy: false },
)

const emit = defineEmits<{ close: []; confirm: [] }>()

const input = ref('')
const canConfirm = computed(() => props.requireText === '' || input.value === props.requireText)

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape' && !props.busy) {
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="modal-mask" @click.self="busy ? undefined : emit('close')">
    <div class="modal-card">
      <h2 class="modal-title">{{ title }}</h2>
      <p v-if="text" class="confirm-text">{{ text }}</p>

      <label v-if="requireText" class="field">
        <span class="field-label">输入「{{ requireText }}」以确认</span>
        <input v-model="input" class="field-input" type="text" autocomplete="off" :disabled="busy" autofocus />
      </label>

      <div class="modal-actions">
        <button class="btn-ghost" type="button" :disabled="busy" @click="emit('close')">取消</button>
        <button
          class="btn-ghost"
          :class="danger ? 'confirm-danger' : 'btn-primary'"
          type="button"
          :disabled="busy || !canConfirm"
          @click="emit('confirm')"
        >
          {{ busy ? '执行中…' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-text {
  margin: 0 0 14px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--text-muted);
  white-space: pre-line;
}

/* 全局只有绿色 .btn-primary，危险按钮的红色在这里给：只此一处用，不值得进 main.css */
.confirm-danger {
  border: none;
  background: var(--danger);
  color: #fff;
}

.confirm-danger:hover:not(:disabled) {
  background: color-mix(in srgb, var(--danger) 85%, #000);
}
</style>
