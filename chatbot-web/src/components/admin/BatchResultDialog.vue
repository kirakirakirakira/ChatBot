<script setup lang="ts">
import { computed, ref } from 'vue'
import type { BatchUserResult } from '@/types'

/**
 * 批量操作的结果弹窗。
 *
 * 必须存在，不能被一个 toast 替代：批量是**逐条独立提交**的（后端不是一个大事务），
 * 「成功 3 / 失败 1」是正常结果而不是报错。失败的每一条都要把后端给的原因摊开——
 * 只说「部分失败」的话，管理员得回列表里逐行猜是谁没动、为什么没动。
 *
 * 「批量重置密码 + 随机生成」时这里还是那些一次性密码**唯一的展示机会**：
 * 后端只回显这一次，关掉弹窗就再也查不回来，所以给逐条复制和「复制全部」。
 */
const props = defineProps<{
  result: BatchUserResult
  /** 动作中文名（后端 BatchUserAction.label 的口径），只用于标题。 */
  actionLabel: string
}>()

const emit = defineEmits<{ close: [] }>()

const failed = computed(() => props.result.items.filter((i) => !i.success))
const passwords = computed(() =>
  props.result.items.filter((i) => i.success && i.generatedPassword !== null),
)
const allOk = computed(() => props.result.failed === 0)
const copied = ref(false)
const copyError = ref('')

async function copyAll(): Promise<void> {
  const text = passwords.value
    // ?? '' 只是给类型看的：上面 filter 已经保证 generatedPassword 非空，但 filter 不会收窄元素类型
    .map((i) => (i.username ?? String(i.id)) + ': ' + (i.generatedPassword ?? ''))
    .join('\n')
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
  } catch {
    copyError.value = '复制失败，请手动选中复制'
  }
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-card wide">
      <h2 class="modal-title">
        {{ actionLabel }}完成
        <span class="summary" :class="{ bad: result.failed > 0 }">
          成功 {{ result.succeeded }} / 失败 {{ result.failed }}（共 {{ result.requested }}）
        </span>
      </h2>

      <p v-if="allOk" class="tip">全部处理成功。</p>
      <template v-else>
        <p class="tip">
          下面这些没处理成功。批量是逐条独立提交的，成功的已经生效，不用因为这几条重做一遍。
        </p>
        <ul class="fail-list">
          <li v-for="item in failed" :key="item.id">
            <span class="fail-name">{{ item.username ?? '未知用户' }}</span>
            <span class="fail-id">#{{ item.id }}</span>
            <span class="fail-msg">{{ item.message ?? '操作失败' }}</span>
          </li>
        </ul>
      </template>

      <template v-if="passwords.length > 0">
        <div class="pwd-head">
          <span class="pwd-title">一次性新密码（关掉弹窗就再也查不回来）</span>
          <button class="btn-ghost mini" type="button" @click="copyAll">{{ copied ? '已复制' : '复制全部' }}</button>
        </div>
        <ul class="pwd-list">
          <li v-for="item in passwords" :key="item.id">
            <span class="fail-name">{{ item.username ?? '未知用户' }}</span>
            <code class="pwd">{{ item.generatedPassword }}</code>
          </li>
        </ul>
        <p v-if="copyError" class="alert-error">{{ copyError }}</p>
        <p class="tip">这些账号下次登录会被强制改密码，请把对应密码转告本人。</p>
      </template>

      <div class="modal-actions">
        <button class="btn-primary" type="button" @click="emit('close')">知道了</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.summary {
  margin-left: 8px;
  font-size: 12px;
  font-weight: normal;
  color: var(--accent);
}

.summary.bad {
  color: var(--danger);
}

.tip {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-muted);
}

.fail-list,
.pwd-list {
  max-height: 200px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
}

.fail-list li,
.pwd-list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--border);
  font-size: 12.5px;
}

.fail-list li:last-child,
.pwd-list li:last-child {
  border-bottom: none;
}

.fail-name {
  font-weight: 550;
}

.fail-id {
  color: var(--text-muted);
  font-size: 11.5px;
}

.fail-msg {
  margin-left: auto;
  color: var(--danger);
  text-align: right;
}

.pwd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.pwd-title {
  font-size: 12.5px;
  color: var(--text-muted);
}

.pwd {
  margin-left: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.4px;
}

.mini {
  padding: 3px 9px;
  font-size: 12px;
}
</style>
