<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchMyStats, revokeMySessions, updateMyProfile } from '@/api'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import SystemPromptDialog from '@/components/SystemPromptDialog.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { clearSession, currentUser, displayName, setSession, token } from '@/auth'
import type { CurrentUser, LoginResult, MyStats } from '@/types'

/**
 * 个人信息（/profile）——每个登录用户都能进的模块，不用管理员。
 *
 * 一屏放四件事，都是「只能管自己」的：
 * ① 基本资料（昵称 / 邮箱 / 手机号，可改）② 账号信息（角色 / 状态 / 时间，只读）
 * ③ 使用统计（会话 / 消息 / 图片，只读）④ 安全（改密码、改人设、退出所有设备）。
 *
 * **登录名刻意不可改**：username 是审计日志里的行为人快照，允许本人随手改名，
 * 等于允许他把「谁干的」这条线索抹掉。要改名请让管理员重建账号（后端 UpdateProfileRequest 同一条注释）。
 *
 * 改密码 / 改人设复用的是聊天页那两个弹窗组件，不在这里重画一份：
 * 强制改密（mustChangePassword）的关不掉语义、改完换发 token 的处理都只有一份实现。
 */

const stats = ref<MyStats | null>(null)
const statsError = ref('')
const loadingStats = ref(true)

const nickname = ref('')
const email = ref('')
const phone = ref('')
const saving = ref(false)
const saveError = ref('')
const saveOk = ref('')

const showPasswordDialog = ref(false)
const showPromptDialog = ref(false)
const showRevokeConfirm = ref(false)
const revoking = ref(false)

/** 资料草稿：从登录态里读一次，之后由用户改；保存成功后用后端回传的用户信息重新对齐。 */
function syncDraft(user: CurrentUser | null): void {
  nickname.value = user?.nickname ?? ''
  email.value = user?.email ?? ''
  phone.value = user?.phone ?? ''
}

const dirty = computed(() => {
  const user = currentUser.value
  if (user === null) {
    return false
  }
  return nickname.value !== (user.nickname ?? '')
    || email.value !== (user.email ?? '')
    || phone.value !== (user.phone ?? '')
})

/** 头像首字母跟着展示名走：设了昵称就该看到昵称的首字，而不是登录名的。 */
const avatarChar = computed(() => (displayName.value || '?').charAt(0).toUpperCase())

const statsCards = computed(() => {
  const s = stats.value
  return [
    { label: '会话', value: s === null ? '—' : String(s.conversationCount) },
    { label: '消息', value: s === null ? '—' : String(s.messageCount) },
    { label: '图片附件', value: s === null ? '—' : String(s.attachmentCount) },
  ]
})

async function loadStats(): Promise<void> {
  loadingStats.value = true
  statsError.value = ''
  try {
    stats.value = await fetchMyStats()
  } catch (e) {
    statsError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loadingStats.value = false
  }
}

onMounted(() => {
  syncDraft(currentUser.value)
  void loadStats()
})

async function save(): Promise<void> {
  if (saving.value || !dirty.value) {
    return
  }
  saving.value = true
  saveError.value = ''
  saveOk.value = ''
  try {
    const user = await updateMyProfile({
      nickname: nickname.value,
      email: email.value,
      phone: phone.value,
    })
    // 后端不换发 token（改资料不作废登录态），所以沿用当前 token、只覆盖用户信息
    if (token.value) {
      setSession(token.value, user)
    }
    syncDraft(user)
    saveOk.value = '已保存'
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}

function onPasswordChanged(result: LoginResult): void {
  // 改密码后端会换发新 token，必须换上，否则下一个请求就 401
  setSession(result.token, result.user)
  syncDraft(result.user)
  saveOk.value = '密码已修改'
}

function onPromptChanged(user: CurrentUser): void {
  if (token.value) {
    setSession(token.value, user)
  }
  saveOk.value = '系统提示词已保存'
}

/**
 * 退出所有设备：后端把 password_changed_at 推到当前时间，本人所有 token（含当前这个）下一个请求就 401。
 * 所以这里必须自己 clearSession()，App.vue 的全局 watch 会把界面送回登录页。
 */
async function revokeAll(): Promise<void> {
  if (revoking.value) {
    return
  }
  revoking.value = true
  saveError.value = ''
  try {
    await revokeMySessions()
    showRevokeConfirm.value = false
    clearSession()
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e)
    showRevokeConfirm.value = false
  } finally {
    revoking.value = false
  }
}

function formatTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) {
    return '—'
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}
</script>

<template>
  <div class="profile-page">
    <header class="profile-head">
      <span class="head-avatar">{{ avatarChar }}</span>
      <div>
        <h1 class="profile-title">个人信息</h1>
        <p class="profile-sub">
          {{ displayName }}
          <span v-if="currentUser?.nickname" class="muted">（登录名 {{ currentUser.username }}）</span>
        </p>
      </div>
    </header>

    <div v-if="saveError" class="alert-error banner">{{ saveError }}</div>
    <div v-else-if="saveOk" class="alert-ok banner">{{ saveOk }}</div>

    <div class="profile-grid">
      <section class="card">
        <h2 class="card-title">基本资料</h2>
        <form class="card-body" @submit.prevent="save">
          <label class="field">
            <span class="field-label">登录名</span>
            <input class="field-input" type="text" :value="currentUser?.username ?? ''" disabled />
            <span class="field-hint">登录名不可修改：它是操作记录里「谁干的」的唯一锚点。</span>
          </label>
          <label class="field">
            <span class="field-label">昵称</span>
            <input v-model="nickname" class="field-input" type="text" maxlength="50" placeholder="没填就显示登录名" :disabled="saving" />
          </label>
          <label class="field">
            <span class="field-label">邮箱</span>
            <input v-model="email" class="field-input" type="email" maxlength="100" placeholder="name@example.com" :disabled="saving" />
          </label>
          <label class="field">
            <span class="field-label">手机号</span>
            <input v-model="phone" class="field-input" type="tel" maxlength="30" placeholder="选填" :disabled="saving" />
          </label>
          <div class="card-actions">
            <button class="btn-ghost" type="button" :disabled="saving || !dirty" @click="syncDraft(currentUser)">撤销改动</button>
            <button class="btn-primary" type="submit" :disabled="saving || !dirty">{{ saving ? '保存中…' : '保存资料' }}</button>
          </div>
        </form>
      </section>

      <div class="stack">
        <section class="card">
          <h2 class="card-title">账号信息</h2>
          <dl class="card-body info-list">
            <div class="info-row">
              <dt>角色</dt>
              <dd>{{ currentUser?.roleLabel ?? '—' }}</dd>
            </div>
            <div class="info-row">
              <dt>状态</dt>
              <dd>
                {{ currentUser?.statusLabel ?? '启用' }}
                <span v-if="currentUser?.mustChangePassword" class="flag" title="管理员重置过密码，本人还没改">待改密</span>
              </dd>
            </div>
            <div class="info-row">
              <dt>创建时间</dt>
              <dd>{{ formatTime(currentUser?.createdAt) }}</dd>
            </div>
            <div class="info-row">
              <dt>上次登录</dt>
              <dd>{{ formatTime(currentUser?.lastLoginAt) }}</dd>
            </div>
          </dl>
        </section>

        <section class="card">
          <h2 class="card-title">使用统计</h2>
          <div class="card-body">
            <div v-if="statsError" class="alert-error">{{ statsError }}</div>
            <div v-else class="stat-row">
              <div v-for="s in statsCards" :key="s.label" class="stat">
                <span class="stat-value">{{ loadingStats ? '…' : s.value }}</span>
                <span class="stat-label">{{ s.label }}</span>
              </div>
            </div>
            <p class="field-hint">统计的是你自己名下的数据，只有你和管理员在删号时才会碰到它。</p>
          </div>
        </section>
      </div>

      <section class="card">
        <h2 class="card-title">个性化与安全</h2>
        <div class="card-body action-list">
          <button class="action-row" type="button" @click="showPromptDialog = true">
            <span class="action-name">系统提示词（人设）</span>
            <span class="action-desc">每轮对话都会作为 system 消息放在历史最前面，所有会话共用。</span>
          </button>
          <button class="action-row" type="button" @click="showPasswordDialog = true">
            <span class="action-name">修改密码</span>
            <span class="action-desc">改完当前设备会自动换上新登录态，其他设备的登录态立刻失效。</span>
          </button>
          <button class="action-row danger" type="button" @click="showRevokeConfirm = true">
            <span class="action-name">退出所有设备</span>
            <span class="action-desc">作废你在所有设备上的登录态（含当前这台），不改密码，之后用原密码重新登录。</span>
          </button>
        </div>
      </section>
    </div>

    <ChangePasswordDialog v-if="showPasswordDialog" @close="showPasswordDialog = false" @changed="onPasswordChanged" />
    <SystemPromptDialog
      v-if="showPromptDialog"
      :initial="currentUser?.systemPrompt ?? ''"
      @close="showPromptDialog = false"
      @changed="onPromptChanged"
    />
    <ConfirmDialog
      v-if="showRevokeConfirm"
      title="退出所有设备"
      text="将作废你在所有设备上的登录态，包括当前这一台。密码不变，之后可以用原密码重新登录。"
      confirm-label="退出所有设备"
      :busy="revoking"
      danger
      @close="showRevokeConfirm = false"
      @confirm="revokeAll"
    />
  </div>
</template>

<style scoped>
.profile-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px 40px;
}

.profile-head,
.banner,
.profile-grid {
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.head-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 55%, #2b6cb0));
  color: #fff;
  font-size: 18px;
  font-weight: 650;
}

.profile-title {
  margin: 0;
  font-size: 20px;
}

.profile-sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--text-muted);
}

.muted {
  color: var(--text-muted);
}

.banner {
  margin-bottom: 14px;
}

/* 两列：左栏是唯一的表单，右栏叠两张只读卡片。窄屏塌成一列，卡片顺序不变。 */
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.profile-grid > .card:last-child {
  grid-column: 1 / -1;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

@media (width <= 860px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  box-shadow: var(--shadow-1);
  overflow: hidden;
}

.card-title {
  margin: 0;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
}

.field-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 2px;
}

.info-list {
  margin: 0;
  gap: 10px;
}

.info-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.info-row dt {
  font-size: 13px;
  color: var(--text-muted);
}

.info-row dd {
  margin: 0;
  font-size: 13.5px;
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

.stat-row {
  display: flex;
  gap: 12px;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 12px 8px;
  background: var(--panel-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
}

.stat-value {
  font-size: 20px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
}

/* 安全区做成整行可点：这三项都只有一个动作，用「标题 + 说明」的大按钮比一排小按钮好点中 */
.action-list {
  gap: 10px;
}

.action-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  background: var(--panel-2);
  text-align: left;
  cursor: pointer;
  transition: border-color 120ms ease, background 120ms ease;
}

.action-row:hover {
  border-color: var(--border-strong);
  background: var(--panel);
}

.action-name {
  font-size: 13.5px;
  font-weight: 550;
}

.action-desc {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}

.action-row.danger .action-name {
  color: var(--danger);
}

.action-row.danger:hover {
  border-color: color-mix(in srgb, var(--danger) 35%, transparent);
}
</style>
