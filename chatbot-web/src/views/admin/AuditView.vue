<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAdminAudit } from '@/api/userAdmin'
import type { AdminAuditLog } from '@/types'

/**
 * 管理端操作审计（/admin/audit）。
 *
 * 只读，且界面上刻意不给任何「删除 / 清空记录」的按钮：能删的审计不叫审计。
 * 记录的产生在后端 AdminAuditService.record()，与业务操作同事务——操作回滚则不留痕，
 * 所以这里看到的每一行都对应一次**真的成功了**的管理操作。
 */
const PAGE_SIZE = 20

const logs = ref<AdminAuditLog[]>([])
const loading = ref(true)
const error = ref('')
const page = ref(0)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listAdminAudit({ page: page.value, size: PAGE_SIZE })
    logs.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(load)

function gotoPage(target: number): void {
  if (target < 0 || target >= totalPages.value) {
    return
  }
  page.value = target
  void load()
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return '—'
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
}
</script>

<template>
  <div class="admin-page">
    <header class="admin-head">
      <div>
        <h1 class="admin-title">操作记录</h1>
        <p class="admin-sub">共 {{ total }} 条 · 只记成功的写操作，与业务同事务（回滚不留痕）</p>
      </div>
      <RouterLink class="btn-ghost head-link" to="/admin/users">返回用户管理</RouterLink>
    </header>

    <div v-if="error" class="alert-error">{{ error }}</div>

    <div v-else-if="loading" class="admin-loading">加载中…</div>
    <table v-else class="audit-table">
      <thead>
        <tr>
          <th>时间</th>
          <th>操作者</th>
          <th>动作</th>
          <th>目标</th>
          <th>明细</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="l in logs" :key="l.id">
          <td class="muted time">{{ formatTime(l.createdAt) }}</td>
          <td>{{ l.actorName }}</td>
          <td><span class="action-tag">{{ l.actionLabel }}</span></td>
          <td>
            {{ l.targetName }}<span class="muted target-id">#{{ l.targetId }}</span>
          </td>
          <td class="muted detail">{{ l.detail ?? '—' }}</td>
        </tr>
        <tr v-if="logs.length === 0">
          <td colspan="5" class="empty">还没有操作记录</td>
        </tr>
      </tbody>
    </table>

    <footer class="admin-pager">
      <button class="btn-ghost mini" type="button" :disabled="page === 0 || loading" @click="gotoPage(page - 1)">上一页</button>
      <span class="pager-text">第 {{ page + 1 }} / {{ totalPages }} 页</span>
      <button class="btn-ghost mini" type="button" :disabled="page + 1 >= totalPages || loading" @click="gotoPage(page + 1)">下一页</button>
    </footer>
  </div>
</template>

<style scoped>
.admin-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px 40px;
}

.admin-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  max-width: 1080px;
  margin: 0 auto 18px;
}

.head-link {
  text-decoration: none;
}

.admin-title {
  margin: 0;
  font-size: 20px;
}

.admin-sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--text-muted);
}

.admin-loading {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.audit-table {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  border-collapse: collapse;
  font-size: 13px;
}

.audit-table th,
.audit-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

.audit-table th {
  color: var(--text-muted);
  font-weight: normal;
}

.muted {
  color: var(--text-muted);
}

.time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.action-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--panel-2);
  border: 1px solid var(--border);
  font-size: 12px;
  white-space: nowrap;
}

.target-id {
  margin-left: 4px;
  font-size: 11px;
}

.detail {
  max-width: 320px;
}

.empty {
  padding: 28px 0;
  text-align: center;
  color: var(--text-muted);
}

.admin-pager {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 1080px;
  margin: 14px auto 0;
}

.mini {
  padding: 3px 9px;
  font-size: 12px;
}

.pager-text {
  font-size: 12.5px;
  color: var(--text-muted);
}
</style>
