<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AgentStep, Citation } from '@/types'
import AgentStepList from '@/components/AgentStepList.vue'
import CitationList from '@/components/CitationList.vue'

/**
 * 查询过程 + 引用卡的统一折叠块。
 * - 流式期间默认展开（能看 AI 正在搜什么），但用户随时可以点摘要收起；
 * - 刷新 / 切会话后 replay=true，默认收起；
 * - 摘要同时显示步数和引用条数，收起时也能看到进度。
 */
const props = defineProps<{
  steps: AgentStep[]
  citations: Citation[]
  status?: string
  replay?: boolean
}>()

/** 展开态由组件自己维护：默认值跟随 replay，之后完全听用户的点击。 */
const open = ref(!props.replay)
watch(() => props.replay, (replay) => {
  open.value = !replay
})

function onToggle(event: Event): void {
  const el = event.target as HTMLDetailsElement
  open.value = el.open
}

const hasContent = computed(() =>
  props.steps.length > 0 || Boolean(props.status) || props.citations.length > 0)

const summary = computed(() => {
  const parts: string[] = []
  if (props.steps.length > 0) {
    parts.push(`查询过程（${props.steps.length} 步）`)
  }
  if (props.citations.length > 0) {
    parts.push(`引用（${props.citations.length} 条）`)
  }
  if (parts.length === 0 && props.status) {
    parts.push('查询中…')
  }
  return parts.join(' · ')
})
</script>

<template>
  <details v-if="hasContent" class="agent-timeline" :open="open" @toggle="onToggle">
    <summary>{{ summary }}</summary>
    <AgentStepList v-if="steps.length > 0 || status" :steps="steps" :status="status" />
    <CitationList :citations="citations" />
  </details>
</template>

<style scoped>
.agent-timeline {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: color-mix(in srgb, var(--accent) 4%, var(--panel-2));
  font-size: 12.5px;
}

.agent-timeline > summary {
  width: fit-content;
  cursor: pointer;
  user-select: none;
  color: var(--text-muted);
  font-size: 12px;
}

.agent-timeline > summary:hover {
  color: var(--text);
}

.agent-timeline[open] > summary {
  margin-bottom: 7px;
  color: var(--text);
}
</style>
