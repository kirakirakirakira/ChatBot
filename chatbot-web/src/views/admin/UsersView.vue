<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  deleteAdminUser,
  fetchUserAdminOptions,
  listAdminUsers,
  revokeAdminUserSessions,
  updateUserRole,
  updateUserStatus,
} from '@/api/userAdmin'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import ResetPasswordDialog from '@/components/admin/ResetPasswordDialog.vue'
import UserFormDialog from '@/components/admin/UserFormDialog.vue'
import { ROLE_ADMIN } from '@/auth'
import type { AdminUser, UserAdminOptions } from '@/types'

/**
 * 用户管理模块（/admin/users）——和聊天平级的第一个功能模块。
 * 路由记录在 router/routes.ts 里带 meta.requiresAdmin：守卫自动拦权限、导航条自动出图标，
 * 本文件不需要关心自己「挂在哪个 URL 上」。
 *
 * 角色 / 状态的下拉选项来自 /options 字典接口，前端不写死 0/1——后端以后加角色这里不用改。
 */
const PAGE_SIZE = 20

const users = ref<AdminUser[]>([])
const options = ref<UserAdminOptions | null>(null)
const loading = ref(true)
const error = ref('')

const keywordInput = ref('')
const keyword = ref('')
const roleSel = ref('')
const statusSel = ref('')
const page = ref(0)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

const showCreate = ref(false)
const resetTarget = ref<AdminUser | null>(null)

type ConfirmKind = 'disable' | 'revoke' | 'delete'
const confirmState = ref<{ kind: ConfirmKind; user: AdminUser } | null>(null)
const busy = ref(false)

/** 三种危险操作的确认文案集中在这里，模板只负责渲染。删号要求输入用户名，挡手滑。 */
const confirmMeta = computed(() => {
  const s = confirmState.value
  if (s === null) {
    return null
  }
  if (s.kind === 'disable') {
    return {
      title: '禁用账号',
      text: '禁用后「' + s.user.username + '」立刻无法登录，已登录的会话在下一个请求时失效。可以随时再启用。',
      confirmLabel: '禁用',
      requireText: '',
    }
  }
  if (s.kind === 'revoke') {
    return {
      title: '强制下线',
      text: '踢掉「' + s.user.username + '」在所有设备上的登录态，不修改密码。',
      confirmLabel: '强制下线',
      requireText: '',
    }
  }
  return {
    title: '删除用户',
    text: '将永久删除「' + s.user.username + '」以及其名下的全部会话、消息与图片附件，无法恢复。',
    confirmLabel: '永久删除',
    requireText: s.user.username,
  }
})

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await listAdminUsers({
      keyword: keyword.value || undefined,
      role: roleSel.value === '' ? null : Number(roleSel.value),
      status: statusSel.value === '' ? null : Number(statusSel.value),
      page: page.value,
      size: PAGE_SIZE,
    })
    users.value = result.items
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    options.value = await fetchUserAdminOptions()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
  await load()
})

function search(): void {
  keyword.value = keywordInput.value.trim()
  page.value = 0
  void load()
}

function onFilterChange(): void {
  page.value = 0
  void load()
}

function gotoPage(target: number): void {
  if (target < 0 || target >= totalPages.value) {
    return
  }
  page.value = target
  void load()
}

function replaceRow(updated: AdminUser): void {
  const i = users.value.findIndex((u) => u.id === updated.id)
  if (i >= 0) {
    users.value[i] = updated
  }
}

/** 行内改角色 / 改状态失败时整页重拉：界面不能和后端不一致地挂着。 */
async function mutate(run: () => Promise<AdminUser>): Promise<void> {
  error.value = ''
  try {
    replaceRow(await run())
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    await load()
  }
}

function onRoleChange(user: AdminUser, role: number): void {
  void mutate(() => updateUserRole(user.id, role))
}

function toggleStatus(user: AdminUser): void {
  // status：0=启用 1=禁用。禁用是不可逆感更强的操作，走确认；启用直接做。
  if (user.status === 0) {
    confirmState.value = { kind: 'disable', user }
    return
  }
  void mutate(() => updateUserStatus(user.id, 0))
}

async function runConfirm(): Promise<void> {
  const state = confirmState.value
  if (state === null || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    if (state.kind === 'disable') {
      replaceRow(await updateUserStatus(state.user.id, 1))
    } else if (state.kind === 'revoke') {
      await revokeAdminUserSessions(state.user.id)
    } else {
      await deleteAdminUser(state.user.id)
      // 删掉本页最后一条时往前退一页，否则停在一张空表上
      if (users.value.length === 1 && page.value > 0) {
        page.value -= 1
      }
    }
    confirmState.value = null
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

function onCreated(): void {
  showCreate.value = false
  page.value = 0
  void load()
}

function formatTime(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return '—'
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}
</script>

<template>
  <div class="admin-page">
    <header class="admin-head">
      <div>
        <h1 class="admin-title">用户管理</h1>
        <p class="admin-sub">共 {{ total }} 个账号</p>
      </div>
      <div class="head-actions">
        <RouterLink class="btn-ghost head-link" to="/admin/audit">操作记录</RouterLink>
        <button class="btn-ghost btn-primary" type="button" @click="showCreate = true">新建用户</button>
      </div>
    </header>

    <div class="admin-toolbar">
      <input
        v-model="keywordInput"
        class="field-input search"
        type="search"
        placeholder="按用户名搜索"
        @keyup.enter="search"
      />
      <button class="btn-ghost" type="button" @click="search">搜索</button>
      <select v-model="roleSel" class="field-input filter" @change="onFilterChange">
        <option value="">全部角色</option>
        <option v-for="r in options?.roles ?? []" :key="r.code" :value="String(r.code)">{{ r.label }}</option>
      </select>
      <select v-model="statusSel" class="field-input filter" @change="onFilterChange">
        <option value="">全部状态</option>
        <option v-for="s in options?.statuses ?? []" :key="s.code" :value="String(s.code)">{{ s.label }}</option>
      </select>
    </div>

    <div v-if="error" class="alert-error">{{ error }}</div>

    <div v-if="loading" class="admin-loading">加载中…</div>
    <table v-else class="user-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>用户名</th>
          <th>角色</th>
          <th>状态</th>
          <th>最近登录</th>
          <th>创建时间</th>
          <th class="col-actions">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.id">
          <td class="num">{{ u.id }}</td>
          <td>
            <span class="uname">{{ u.username }}</span>
            <span v-if="u.mustChangePassword" class="flag" title="管理员重置过密码，本人还没改">待改密</span>
          </td>
          <td>
            <select
              class="field-input inline"
              :value="String(u.role)"
              @change="onRoleChange(u, Number(($event.target as HTMLSelectElement).value))"
            >
              <option v-for="r in options?.roles ?? []" :key="r.code" :value="String(r.code)">{{ r.label }}</option>
            </select>
          </td>
          <td>
            <span class="role-tag" :class="{ off: u.status !== 0 }">{{ u.statusLabel }}</span>
            <button class="btn-ghost mini" type="button" @click="toggleStatus(u)">
              {{ u.status === 0 ? '禁用' : '启用' }}
            </button>
          </td>
          <td class="muted">{{ formatTime(u.lastLoginAt) }}</td>
          <td class="muted">{{ formatTime(u.createdAt) }}</td>
          <td class="col-actions">
            <button class="btn-ghost mini" type="button" @click="resetTarget = u">重置密码</button>
            <button class="btn-ghost mini" type="button" @click="confirmState = { kind: 'revoke', user: u }">强制下线</button>
            <button class="btn-ghost mini danger" type="button" @click="confirmState = { kind: 'delete', user: u }">删除</button>
          </td>
        </tr>
        <tr v-if="users.length === 0">
          <td colspan="7" class="empty">没有匹配的账号</td>
        </tr>
      </tbody>
    </table>

    <footer class="admin-pager">
      <button class="btn-ghost mini" type="button" :disabled="page === 0 || loading" @click="gotoPage(page - 1)">上一页</button>
      <span class="pager-text">第 {{ page + 1 }} / {{ totalPages }} 页</span>
      <button class="btn-ghost mini" type="button" :disabled="page + 1 >= totalPages || loading" @click="gotoPage(page + 1)">下一页</button>
    </footer>

    <UserFormDialog v-if="showCreate && options" :options="options" @close="showCreate = false" @created="onCreated" />
    <ResetPasswordDialog v-if="resetTarget" :user="resetTarget" @close="resetTarget = null" @changed="load" />
    <ConfirmDialog
      v-if="confirmState !== null && confirmMeta !== null"
      :title="confirmMeta.title"
      :text="confirmMeta.text"
      :confirm-label="confirmMeta.confirmLabel"
      :require-text="confirmMeta.requireText"
      :busy="busy"
      danger
      @close="confirmState = null"
      @confirm="runConfirm"
    />
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

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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

.admin-toolbar {
  display: flex;
  gap: 8px;
  max-width: 1080px;
  margin: 0 auto 14px;
}

.search {
  width: 220px;
}

.filter {
  width: 130px;
}

.admin-loading {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.user-table {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  border-collapse: collapse;
  font-size: 13px;
}

.user-table th,
.user-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}

.user-table th {
  color: var(--text-muted);
  font-weight: normal;
}

.num,
.muted {
  color: var(--text-muted);
}

.uname {
  font-weight: 550;
}

.flag {
  margin-left: 6px;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--danger) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger) 30%, transparent);
  color: var(--danger);
  font-size: 11px;
}

.field-input.inline {
  width: 108px;
  padding: 4px 8px;
  font-size: 12.5px;
}

.role-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 30%, transparent);
  color: var(--accent);
}

.role-tag.off {
  background: var(--panel-2);
  border-color: var(--border);
  color: var(--text-muted);
}

.mini {
  padding: 3px 9px;
  font-size: 12px;
}

.danger,
.danger:hover:not(:disabled) {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 35%, transparent);
}

.col-actions {
  white-space: nowrap;
}

.col-actions .mini {
  margin-right: 4px;
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

.pager-text {
  font-size: 12.5px;
  color: var(--text-muted);
}
</style>
