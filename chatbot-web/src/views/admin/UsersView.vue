<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import {
  batchAdminUsers,
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
import BatchResetPasswordDialog from '@/components/admin/BatchResetPasswordDialog.vue'
import BatchResultDialog from '@/components/admin/BatchResultDialog.vue'
import { ROLE_ADMIN, ROLE_GUEST, ROLE_SUPER_ADMIN, currentUser } from '@/auth'
import type { AdminUser, BatchUserAction, BatchUserResult, UserAdminOptions } from '@/types'

/**
 * 用户管理模块（/admin/users）——和聊天平级的第一个功能模块。
 * 路由记录在 router/routes.ts 里带 meta.requiresAdmin：守卫自动拦权限、导航条自动出图标，
 * 本文件不需要关心自己「挂在哪个 URL 上」。
 *
 * 角色 / 状态的下拉选项来自 /options 字典接口，前端不写死 0/1——后端以后加角色这里不用改。
 *
 * 勾选与批量：**只勾当前页**，翻页 / 搜索 / 刷新都会清空选择。跨页累积选择看起来更强大，
 * 实际是给自己挖坑——勾选的人不在眼前时，「删除 17 个」这种按钮谁也不敢点。
 * 批量的权限、层级、最后一个管理员等规则全在后端逐条判定，这里只负责把结果摊开给人看。
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

/** 自己的 id：自己的行所有控件锁死（需求明确要求「不能改自己的角色」要锁按键，而不是点了才报错）。 */
const myId = computed(() => currentUser.value?.id ?? -1)

/** 行内控件是否锁死：层级不低于我（canManage=false）、或者就是我自己。 */
function rowLocked(u: AdminUser): boolean {
  return !u.canManage || u.id === myId.value
}

function lockTitle(u: AdminUser): string {
  if (u.id === myId.value) {
    return '不能修改自己的角色与信息'
  }
  if (!u.canManage) {
    return '只能管理层级低于自己的用户'
  }
  return ''
}

/** 角色标签配色：超管最重、管理员次之、访客最淡；禁用态 .off 优先级最高。 */
function roleTagClass(u: AdminUser): string {
  if (u.role === ROLE_SUPER_ADMIN) {
    return 'super'
  }
  if (u.role === ROLE_ADMIN) {
    return 'admin'
  }
  if (u.role === ROLE_GUEST) {
    return 'guest'
  }
  return ''
}

const showCreate = ref(false)
const resetTarget = ref<AdminUser | null>(null)

type ConfirmKind =
  | 'disable' | 'revoke' | 'delete'
  | 'batch-disable' | 'batch-revoke' | 'batch-delete' | 'batch-role'
type ConfirmState =
  | { kind: 'disable' | 'revoke' | 'delete'; user: AdminUser }
  | { kind: 'batch-disable' | 'batch-revoke' | 'batch-delete' }
  | { kind: 'batch-role'; role: number; roleLabel: string }
const confirmState = ref<ConfirmState | null>(null)
const busy = ref(false)

/** ---------- 勾选 ---------- */

const selectedIds = ref<number[]>([])
/** 本页里「能被我操作」的行：勾不了的人（自己、层级不低于我）连复选框都是禁用的。 */
const selectableUsers = computed(() => users.value.filter((u) => !rowLocked(u)))
const selectedUsers = computed(() => selectableUsers.value.filter((u) => selectedIds.value.includes(u.id)))
const allSelected = computed(
  () => selectableUsers.value.length > 0 && selectedUsers.value.length === selectableUsers.value.length,
)
/** 半选态（indeterminate）只能用 DOM 属性表达，没有对应的 HTML attribute，所以要拿 ref 手动设。 */
const someSelected = computed(() => selectedUsers.value.length > 0 && !allSelected.value)
const selectAllBox = ref<HTMLInputElement | null>(null)
watchEffect(() => {
  if (selectAllBox.value) {
    selectAllBox.value.indeterminate = someSelected.value
  }
})

function toggleAll(): void {
  selectedIds.value = allSelected.value ? [] : selectableUsers.value.map((u) => u.id)
}

function toggleOne(id: number): void {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((x) => x !== id)
    : [...selectedIds.value, id]
}

function clearSelection(): void {
  selectedIds.value = []
}

/** 批量改角色的目标角色，null = 还没选（按钮禁用）。选项来自 options.roles，已经按层级过滤过。 */
const batchRole = ref<number | null>(null)
const showBatchPassword = ref(false)
/** 非空 = 有一次批量结果要摊开给人看（成功 / 失败逐条 + 一次性新密码）。 */
const batchResult = ref<{ result: BatchUserResult; actionLabel: string } | null>(null)

const selectedCount = computed(() => selectedUsers.value.length)

function askBatchRole(): void {
  const role = batchRole.value
  if (role === null) {
    return
  }
  const label = options.value?.roles.find((r) => r.code === role)?.label ?? String(role)
  confirmState.value = { kind: 'batch-role', role, roleLabel: label }
}

/** 单条与批量共用的确认文案，集中在这里，模板只负责渲染。 */
const confirmMeta = computed(() => {
  const s = confirmState.value
  if (s === null) {
    return null
  }
  switch (s.kind) {
    case 'disable':
      return {
        title: '禁用账号',
        text: '禁用后「' + s.user.username + '」立刻无法登录，已登录的会话在下一个请求时失效。可以随时再启用。',
        confirmLabel: '禁用',
        requireText: '',
      }
    case 'revoke':
      return {
        title: '强制下线',
        text: '踢掉「' + s.user.username + '」在所有设备上的登录态，不修改密码。',
        confirmLabel: '强制下线',
        requireText: '',
      }
    case 'delete':
      return {
        title: '删除用户',
        text: '将永久删除「' + s.user.username + '」以及其名下的全部会话、消息与图片附件，无法恢复。',
        confirmLabel: '永久删除',
        requireText: s.user.username,
      }
    case 'batch-disable':
      return {
        title: '批量禁用',
        text: '将禁用选中的 ' + selectedCount.value + ' 个账号：他们立刻无法登录，已登录的会话在下一个请求时失效。可以随时再启用。\n'
          + '若其中有人是「最后一个启用的管理员」，那一条会失败并在结果里说明原因。',
        confirmLabel: '禁用 ' + selectedCount.value + ' 个',
        requireText: '',
      }
    case 'batch-revoke':
      return {
        title: '批量强制下线',
        text: '踢掉选中的 ' + selectedCount.value + ' 个账号在所有设备上的登录态，不修改密码，他们可以用原密码重新登录。',
        confirmLabel: '强制下线 ' + selectedCount.value + ' 个',
        requireText: '',
      }
    case 'batch-delete':
      return {
        title: '批量删除',
        text: '将永久删除选中的 ' + selectedCount.value + ' 个账号，以及他们名下的全部会话、消息与图片附件，无法恢复。\n'
          + '这一步不可撤销，请输入选中数量以确认。',
        confirmLabel: '永久删除 ' + selectedCount.value + ' 个',
        requireText: String(selectedCount.value),
      }
    case 'batch-role':
      return {
        title: '批量改角色',
        text: '把选中的 ' + selectedCount.value + ' 个账号的角色改成「' + s.roleLabel + '」，下一个请求就按新角色鉴权。\n'
          + '不能指派层级不低于自己的角色，越界的那几条会失败并在结果里说明原因。',
        confirmLabel: '改成「' + s.roleLabel + '」',
        requireText: '',
      }
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
    // 换页 / 重新筛选之后，上一页的勾选已经没有意义（人都不在眼前了），一律清空
    clearSelection()
    // 批量删掉整页时退回上一页，别停在一张空表上让人以为「账号全没了」
    if (users.value.length === 0 && page.value > 0) {
      page.value -= 1
      await load()
    }
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

/**
 * 执行一次批量。busy 由调用方管（确认框的按钮要转圈），这里只负责发请求 + 摊开结果。
 * 抛出来的异常只可能是「整批级」的（超过 100 个、角色未知、密码太短）；
 * 逐条失败不算异常，它们在 result.items 里，由结果窗展示。
 */
async function doBatch(action: BatchUserAction, actionLabel: string, role?: number): Promise<void> {
  const result = await batchAdminUsers({
    ids: selectedUsers.value.map((u) => u.id),
    action,
    ...(role === undefined ? {} : { role }),
  })
  batchResult.value = { result, actionLabel }
  clearSelection()
  batchRole.value = null
}

/** 批量启用不走确认框：它和单条「启用」一样是可逆的，多问一句只是拖慢操作。 */
async function batchEnable(): Promise<void> {
  if (busy.value || selectedCount.value === 0) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    await doBatch('ENABLE', '批量启用')
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function runConfirm(): Promise<void> {
  const state = confirmState.value
  if (state === null || busy.value) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    switch (state.kind) {
      case 'disable':
        replaceRow(await updateUserStatus(state.user.id, 1))
        break
      case 'revoke':
        await revokeAdminUserSessions(state.user.id)
        break
      case 'delete':
        await deleteAdminUser(state.user.id)
        break
      case 'batch-disable':
        await doBatch('DISABLE', '批量禁用')
        break
      case 'batch-revoke':
        await doBatch('REVOKE_SESSIONS', '批量强制下线')
        break
      case 'batch-delete':
        await doBatch('DELETE', '批量删除')
        break
      case 'batch-role':
        await doBatch('SET_ROLE', '批量改角色', state.role)
        break
    }
    confirmState.value = null
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

/** 批量重置密码的结果从弹窗里回来：同样摊开，密码只在这一次出现。 */
function onBatchPasswordDone(result: BatchUserResult): void {
  batchResult.value = { result, actionLabel: '批量重置密码' }
  clearSelection()
  void load()
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
      <!-- 筛选器用 allRoles（全量角色）而不是 roles：roles 是「我能指派的角色」，
           按层级过滤过，管理员拿不到「管理员 / 超管」两档，可列表里这两档的行是看得见的。
           读操作用读口径，写操作用写口径，别共用一份数据。 -->
      <select v-model="roleSel" class="field-input filter" @change="onFilterChange">
        <option value="">全部角色</option>
        <option v-for="r in options?.allRoles ?? []" :key="r.code" :value="String(r.code)">{{ r.label }}</option>
      </select>
      <select v-model="statusSel" class="field-input filter" @change="onFilterChange">
        <option value="">全部状态</option>
        <option v-for="s in options?.statuses ?? []" :key="s.code" :value="String(s.code)">{{ s.label }}</option>
      </select>
    </div>

    <!-- 批量操作条：只在有勾选时出现，位置固定在表格上方，按钮顺序与单条操作的危险程度一致（越危险越靠右） -->
    <div v-if="selectedCount > 0" class="batch-bar">
      <span class="batch-count">已选 {{ selectedCount }} 项</span>
      <div class="batch-actions">
        <button class="btn-ghost mini" type="button" :disabled="busy" @click="batchEnable">启用</button>
        <button class="btn-ghost mini" type="button" :disabled="busy" @click="confirmState = { kind: 'batch-disable' }">禁用</button>
        <select v-model.number="batchRole" class="field-input mini-select" :disabled="busy" aria-label="批量改成的角色">
          <option :value="null" disabled>改成角色…</option>
          <option v-for="r in options?.roles ?? []" :key="r.code" :value="r.code">{{ r.label }}</option>
        </select>
        <button class="btn-ghost mini" type="button" :disabled="busy || batchRole === null" @click="askBatchRole">改角色</button>
        <button class="btn-ghost mini" type="button" :disabled="busy" @click="showBatchPassword = true">重置密码</button>
        <button class="btn-ghost mini" type="button" :disabled="busy" @click="confirmState = { kind: 'batch-revoke' }">强制下线</button>
        <button class="btn-ghost mini danger" type="button" :disabled="busy" @click="confirmState = { kind: 'batch-delete' }">删除</button>
        <button class="btn-ghost mini" type="button" :disabled="busy" @click="clearSelection">取消选择</button>
      </div>
    </div>

    <div v-if="error" class="alert-error">{{ error }}</div>

    <div v-if="loading" class="admin-loading">加载中…</div>
    <table v-else class="user-table">
      <thead>
        <tr>
          <th class="col-check">
            <input
              ref="selectAllBox"
              type="checkbox"
              :checked="allSelected"
              :disabled="selectableUsers.length === 0"
              :title="selectableUsers.length === 0 ? '本页没有你能操作的账号' : '全选本页可操作的账号'"
              aria-label="全选本页"
              @change="toggleAll"
            />
          </th>
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
        <tr v-for="u in users" :key="u.id" :class="{ picked: selectedIds.includes(u.id) }">
          <td class="col-check">
            <input
              type="checkbox"
              :checked="selectedIds.includes(u.id)"
              :disabled="rowLocked(u)"
              :title="rowLocked(u) ? lockTitle(u) : '选择「' + u.username + '」'"
              :aria-label="'选择 ' + u.username"
              @change="toggleOne(u.id)"
            />
          </td>
          <td class="num">{{ u.id }}</td>
          <td>
            <span class="uname">{{ u.username }}</span>
            <span v-if="u.nickname" class="nick" :title="'昵称：' + u.nickname">{{ u.nickname }}</span>
            <span v-if="u.id === myId" class="flag self" title="这是你自己：角色与信息都锁死">我</span>
            <span v-if="u.mustChangePassword" class="flag" title="管理员重置过密码，本人还没改">待改密</span>
          </td>
          <td>
            <!-- 锁死的行不渲染 select：下拉选项已按「只能指派低于自己的角色」过滤，
                 里面根本没有这一行的当前角色，硬渲染会得到一个空白框。纯文本标签才是它该有的样子。 -->
            <select
              v-if="!rowLocked(u)"
              class="field-input inline"
              :value="String(u.role)"
              @change="onRoleChange(u, Number(($event.target as HTMLSelectElement).value))"
            >
              <option v-for="r in options?.roles ?? []" :key="r.code" :value="String(r.code)">{{ r.label }}</option>
            </select>
            <span v-else class="role-plain" :title="lockTitle(u)">{{ u.roleLabel }}</span>
          </td>
          <td>
            <span class="role-tag" :class="[roleTagClass(u), { off: u.status !== 0 }]">{{ u.statusLabel }}</span>
            <button
              class="btn-ghost mini"
              type="button"
              :disabled="rowLocked(u)"
              :title="lockTitle(u)"
              @click="toggleStatus(u)"
            >
              {{ u.status === 0 ? '禁用' : '启用' }}
            </button>
          </td>
          <td class="muted">{{ formatTime(u.lastLoginAt) }}</td>
          <td class="muted">{{ formatTime(u.createdAt) }}</td>
          <td class="col-actions">
            <button class="btn-ghost mini" type="button" :disabled="rowLocked(u)" :title="lockTitle(u)" @click="resetTarget = u">重置密码</button>
            <button class="btn-ghost mini" type="button" :disabled="rowLocked(u)" :title="lockTitle(u)" @click="confirmState = { kind: 'revoke', user: u }">强制下线</button>
            <button class="btn-ghost mini danger" type="button" :disabled="rowLocked(u)" :title="lockTitle(u)" @click="confirmState = { kind: 'delete', user: u }">删除</button>
          </td>
        </tr>
        <tr v-if="users.length === 0">
          <td colspan="8" class="empty">没有匹配的账号</td>
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
    <BatchResetPasswordDialog
      v-if="showBatchPassword"
      :users="selectedUsers"
      @close="showBatchPassword = false"
      @done="onBatchPasswordDone"
    />
    <BatchResultDialog
      v-if="batchResult !== null"
      :result="batchResult.result"
      :action-label="batchResult.actionLabel"
      @close="batchResult = null"
    />
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

/* ---------- 批量操作条 ----------
   做成一条独立的浮起横条而不是塞进筛选行：筛选是「看哪些人」，批量是「对选中的人做什么」，
   混在一排会让人分不清「禁用」按钮作用于筛选结果还是勾选结果。 */
.batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  max-width: 1080px;
  margin: 0 auto 14px;
  padding: 8px 12px;
  background: var(--accent-soft);
  border: 1px solid color-mix(in srgb, var(--accent) 28%, transparent);
  border-radius: var(--radius-m);
}

.batch-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-strong);
  white-space: nowrap;
}

.batch-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-left: auto;
}

.mini-select {
  width: 118px;
  padding: 3px 8px;
  font-size: 12px;
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

/* 勾选中的行整行淡染：批量操作的影响范围必须一眼看得出来，光靠复选框的勾不够 */
.user-table tbody tr.picked > td {
  background: var(--accent-soft);
}

.col-check {
  width: 34px;
  padding-right: 0;
}

.col-check input {
  margin: 0;
  cursor: pointer;
}

.col-check input:disabled {
  cursor: not-allowed;
}

.num,
.muted {
  color: var(--text-muted);
}

.uname {
  font-weight: 550;
}

.nick {
  margin-left: 6px;
  color: var(--text-muted);
  font-size: 12px;
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

.role-plain {
  font-size: 12.5px;
  color: var(--text-muted);
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
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-muted);
}

.role-tag.admin {
  background: var(--accent-soft);
  border-color: color-mix(in srgb, var(--accent) 30%, transparent);
  color: var(--accent);
}

.role-tag.super {
  background: color-mix(in srgb, var(--accent) 22%, transparent);
  border-color: color-mix(in srgb, var(--accent) 55%, transparent);
  color: var(--accent-strong);
  font-weight: 600;
}

.role-tag.guest {
  border-style: dashed;
}

.flag.self {
  background: var(--panel-2);
  border-color: var(--border);
  color: var(--text-muted);
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
