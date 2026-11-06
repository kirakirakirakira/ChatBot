<script setup lang="ts">
import type { AgentStep } from '@/types'

defineProps<{
  steps: AgentStep[]
  status?: string
}>()

function formatArgs(args: Record<string, unknown>): string {
  if (Object.keys(args).length === 0) {
    return ''
  }
  try {
    return JSON.stringify(args, null, 2)
  } catch {
    return ''
  }
}

function statusText(status: AgentStep['status']): string {
  if (status === 'running') {
    return '执行中'
  }
  if (status === 'ok') {
    return '完成'
  }
  return '失败'
}
</script>

<template>
  <div v-if="status || steps.length > 0" class="agent-timeline">
    <div v-if="status" class="agent-status">
      <span class="pulse"></span>
      {{ status }}
    </div>

    <div v-for="step in steps" :key="step.callId" class="agent-step" :class="step.status">
      <span class="step-dot" aria-hidden="true"></span>
      <div class="step-main">
        <div class="step-head">
          <span class="step-label">{{ step.label }}</span>
          <span class="step-meta">
            {{ statusText(step.status) }}
            <template v-if="step.durationMs != null"> · {{ step.durationMs }}ms</template>
          </span>
        </div>
        <details v-if="formatArgs(step.args)" class="step-args">
          <summary>参数</summary>
          <pre>{{ formatArgs(step.args) }}</pre>
        </details>
        <div v-if="step.summary" class="step-summary">{{ step.summary }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-timeline {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: color-mix(in srgb, var(--accent) 4%, var(--panel-2));
  font-size: 12.5px;
}

.agent-status {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
}

.pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.35; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

.agent-step {
  display: flex;
  gap: 8px;
  padding-top: 6px;
  border-top: 1px solid color-mix(in srgb, var(--border) 65%, transparent);
}

.agent-step:first-of-type {
  padding-top: 0;
  border-top: none;
}

.step-dot {
  flex: none;
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--text-muted);
}

.agent-step.running .step-dot {
  background: var(--accent);
  animation: pulse 1.1s ease-in-out infinite;
}

.agent-step.ok .step-dot {
  background: var(--accent);
}

.agent-step.error .step-dot {
  background: var(--danger);
}

.step-main {
  min-width: 0;
  flex: 1;
}

.step-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.step-label {
  font-weight: 550;
}

.step-meta {
  flex: none;
  font-size: 11.5px;
  color: var(--text-muted);
}

.step-args {
  margin-top: 4px;
  color: var(--text-muted);
}

.step-args summary {
  width: fit-content;
  cursor: pointer;
  user-select: none;
  font-size: 11.5px;
}

.step-args pre {
  margin: 5px 0 0;
  padding: 7px 8px;
  max-height: 160px;
  overflow: auto;
  border-radius: var(--radius-s);
  background: color-mix(in srgb, var(--text) 4%, transparent);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 11.5px;
}

.step-summary {
  margin-top: 4px;
  line-height: 1.55;
  color: var(--text);
  overflow-wrap: anywhere;
}
</style>
