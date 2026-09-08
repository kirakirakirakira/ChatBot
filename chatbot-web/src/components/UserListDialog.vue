<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { listUsers } from '@/api'
import { ROLE_ADMIN } from '@/auth'
import type { CurrentUser } from '@/types'

const emit = defineEmits<{ close: [] }>()

const users = ref<CurrentUser[]>([])
const loading = ref(true)
const error = ref('')

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    emit('close')
  }
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  try {
    users.value = await listUsers()
  } catch (e) {
    // 普通用户误入（后端 403）、或登录态失效（401，api.ts 已经清掉登录态）都走这里
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return ''
  }
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div class="modal-mask" @click.self="emit('close')">
    <div class="modal-card wide">
      <h2 class="modal-title">用户管理<span class="modal-title-note">仅管理员可见</span></h2>

      <div v-if="error" class="alert-error">{{ error }}</div>
      <div v-else-if="loading" class="modal-loading">加载中…</div>
      <table v-else class="user-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>角色</th>
            <th>创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td class="num">{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>
              <span class="role-tag" :class="{ admin: u.role === ROLE_ADMIN }">{{ u.roleLabel }}</span>
              <span class="role-code">role={{ u.role }}</span>
            </td>
            <td class="muted">{{ formatTime(u.createdAt) }}</td>
          </tr>
        </tbody>
      </table>

      <div class="modal-actions">
        <button class="btn-ghost" type="button" @click="emit('close')">关闭</button>
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

.modal-loading {
  padding: 20px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.user-table th,
.user-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}

.user-table th {
  color: var(--text-muted);
  font-weight: normal;
}

.user-table tbody tr:last-child td {
  border-bottom: none;
}

.num {
  color: var(--text-muted);
}

.muted {
  color: var(--text-muted);
}

.role-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--bg);
  border: 1px solid var(--border);
}

.role-tag.admin {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.role-code {
  margin-left: 6px;
  font-size: 11px;
  color: var(--text-muted);
}
</style>