<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { clearSession, currentUser, displayName, isAdmin } from '@/auth'

/**
 * 左侧模块导航条。
 *
 * 条目**从路由表派生**、不在这里写死：router/routes.ts 里凡是带 meta.moduleId 的记录都会自动出现。
 * 所以「加一个和聊天平级的模块」= 加一条路由记录，本文件一行都不用改。
 *
 * requiresAdmin 的模块对普通用户直接不渲染（而不是渲染成禁用态）：看不见的入口比点不动的干净，
 * 真正的拦截在后端 @RequireAdmin 和路由守卫上，这里只是不给人看一个必然 403 的按钮。
 */
interface NavItem {
  path: string
  title: string
  icon: Component
}

const router = useRouter()

/**
 * 展开 / 收起。
 * 默认展开：只有图标时，新用户必须点进去才知道「这本书」是博客——发现性优先于省 120px。
 * 状态存 localStorage：同一个人收起一次，之后每次进应用都保持收起，不用反复点。
 * 读写都包 try/catch：隐私模式 / 禁用存储时不该让整个导航条崩掉，退回默认展开即可。
 */
const NAV_EXPANDED_KEY = 'chatbot.navExpanded'

function readStoredNavExpanded(): boolean {
  try {
    return localStorage.getItem(NAV_EXPANDED_KEY) !== '0'
  } catch {
    return true
  }
}

const expanded = ref(readStoredNavExpanded())

watch(expanded, (value) => {
  try {
    localStorage.setItem(NAV_EXPANDED_KEY, value ? '1' : '0')
  } catch {
    // 存不了就算了：本次会话仍然按用户点的状态显示
  }
})

/**
 * 导航条底部的账号菜单。
 * 为什么放在外壳而不是各模块里：管理页没有聊天顶栏的头像菜单，若「个人信息 / 退出登录」只存在于
 * ChatView，站在 /admin/users 上的人就得先跳回聊天才能退出——外壳级的事不该跟着模块走。
 * 退出登录只调 clearSession()：跳回登录页由 App.vue 的全局 watch 统一负责。
 */
const menuOpen = ref(false)
const accountRoot = ref<HTMLElement | null>(null)

const avatarChar = computed(() => (displayName.value || '?').charAt(0).toUpperCase())
/** 设了昵称才多显示一行登录名：昵称和登录名一样时，重复显示只是噪音。 */
const showUsername = computed(() => displayName.value !== (currentUser.value?.username ?? ''))

function go(path: string): void {
  menuOpen.value = false
  // 已经在这一页就别再 push 一次：vue-router 会当成重复导航，白白产生一次告警
  if (router.currentRoute.value.path !== path) {
    void router.push(path)
  }
}

function logout(): void {
  menuOpen.value = false
  clearSession()
}

function onDocumentMouseDown(e: MouseEvent): void {
  if (menuOpen.value && accountRoot.value && !accountRoot.value.contains(e.target as Node)) {
    menuOpen.value = false
  }
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    menuOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMouseDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMouseDown)
  document.removeEventListener('keydown', onKeydown)
})

const items = computed<NavItem[]>(() =>
  router
    .getRoutes()
    .filter((r) => r.meta.moduleId !== undefined && r.meta.hidden !== true && r.meta.icon !== undefined)
    .filter((r) => r.meta.requiresAdmin !== true || isAdmin.value)
    .sort((a, b) => (a.meta.order ?? 99) - (b.meta.order ?? 99))
    .map((r) => ({ path: r.path, title: r.meta.title ?? r.path, icon: r.meta.icon as Component })),
)
</script>

<template>
  <nav class="module-nav" :class="{ expanded }" aria-label="功能模块">
    <!-- 折叠按钮放在第一项上方：它是导航本身的一部分，不该藏进账号菜单 -->
    <button
      class="nav-toggle"
      type="button"
      :title="expanded ? '收起导航' : '展开导航'"
      :aria-label="expanded ? '收起导航' : '展开导航'"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m15 18-6-6 6-6" />
      </svg>
    </button>
    <!-- 激活态直接用 vue-router 自带的 router-link-active（含子路由匹配），不用自己算 -->
    <RouterLink
      v-for="item in items"
      :key="item.path"
      class="nav-item"
      :to="item.path"
      :title="expanded ? undefined : item.title"
      :aria-label="item.title"
    >
      <component :is="item.icon" class="nav-icon" />
      <span v-if="expanded" class="nav-label">{{ item.title }}</span>
    </RouterLink>

    <div class="nav-spacer" />

    <div ref="accountRoot" class="nav-account">
      <button
        class="avatar-btn"
        type="button"
        :title="displayName"
        :aria-label="displayName"
        :aria-expanded="menuOpen"
        @click="menuOpen = !menuOpen"
      >
        <span class="avatar">{{ avatarChar }}</span>
        <span v-if="expanded" class="account-btn-name" :title="displayName">{{ displayName }}</span>
      </button>
      <div v-if="menuOpen" class="account-menu" role="menu">
        <div class="account-head">
          <div class="account-name" :title="displayName">{{ displayName }}</div>
          <div v-if="showUsername" class="account-username">@{{ currentUser?.username }}</div>
          <div class="account-role">{{ currentUser?.roleLabel }}</div>
        </div>
        <div class="account-sep" />
        <button class="account-item" type="button" role="menuitem" @click="go('/profile')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
          个人信息
        </button>
        <button v-if="isAdmin" class="account-item" type="button" role="menuitem" @click="go('/admin/users')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>
          用户管理
        </button>
        <button v-if="isAdmin" class="account-item" type="button" role="menuitem" @click="go('/admin/audit')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" /><path d="M14 2v6h6" /><path d="M8 13h8" /><path d="M8 17h5" /></svg>
          操作记录
        </button>
        <div class="account-sep" />
        <button class="account-item danger" type="button" role="menuitem" @click="logout">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" /></svg>
          退出登录
        </button>
      </div>
    </div>
  </nav>
</template>

<style scoped>
.module-nav {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 56px;
  height: 100%;
  padding: 10px 8px;
  box-sizing: border-box;
  background: var(--panel-2);
  border-right: 1px solid var(--border);
  transition: width 160ms ease;
}

/* 展开态：176px 足够放下「个人信息 / 用户管理 / 操作记录」这类最长 4 个字的标签 */
.module-nav.expanded {
  width: 176px;
  align-items: stretch;
}

/* 折叠按钮：展开时朝左（点了收起），折叠时旋转 180° 朝右（点了展开） */
.nav-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 100%;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.nav-toggle:hover {
  color: var(--text);
  background: color-mix(in srgb, var(--text) 6%, transparent);
}

.nav-toggle svg {
  transition: transform 160ms ease;
}

.module-nav:not(.expanded) .nav-toggle svg {
  transform: rotate(180deg);
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  flex-shrink: 0;
  width: 100%;
  height: 38px;
  padding: 0 9px;
  box-sizing: border-box;
  border-radius: var(--radius-m);
  color: var(--text-muted);
  text-decoration: none;
  overflow: hidden;
  transition: background 120ms ease, color 120ms ease;
}

/* 折叠态回到原来的 38×38 居中图标；展开态才左对齐并显示文字 */
.module-nav:not(.expanded) .nav-item {
  justify-content: center;
  padding: 0;
}

.nav-icon {
  flex-shrink: 0;
}

.nav-label {
  min-width: 0;
  font-size: 13.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-item:hover {
  color: var(--text);
  background: color-mix(in srgb, var(--text) 6%, transparent);
}

.nav-item.router-link-active {
  color: var(--accent);
  background: var(--accent-soft);
}

.nav-spacer {
  flex: 1;
}

.nav-account {
  position: relative;
  width: 100%;
}

.avatar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin: 0 auto;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  cursor: pointer;
}

/* 展开态把头像按钮变成整行「头像 + 名字」，和上面的导航项对齐 */
.module-nav.expanded .avatar-btn {
  justify-content: flex-start;
  gap: 10px;
  width: 100%;
  height: 40px;
  padding: 0 9px;
  margin: 0;
  box-sizing: border-box;
  border-radius: var(--radius-m);
}

.avatar-btn:hover {
  background: color-mix(in srgb, var(--text) 7%, transparent);
}

.account-btn-name {
  min-width: 0;
  color: var(--text);
  font-size: 13.5px;
  font-weight: 550;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 55%, #2b6cb0));
  color: #fff;
  font-size: 13px;
  font-weight: 650;
}

.account-menu {
  /* 菜单贴着导航条右侧弹出，折叠 / 展开两种宽度下都不会出屏。
     展开态 nav-account 宽 100%，left: 100% 正好落在导航条右边。 */
  position: absolute;
  left: calc(100% + 10px);
  bottom: 0;
  z-index: 40;
  min-width: 208px;
  padding: 6px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  box-shadow: var(--shadow-2);
  animation: account-menu-in 130ms cubic-bezier(0.2, 0.9, 0.3, 1);
}

@keyframes account-menu-in {
  from {
    opacity: 0;
    transform: translateX(-4px);
  }
}

.account-head {
  padding: 8px 10px 6px;
}

.account-name {
  font-size: 13.5px;
  font-weight: 600;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-username {
  font-size: 11.5px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-role {
  display: inline-block;
  margin-top: 5px;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 11px;
}

.account-sep {
  height: 1px;
  margin: 5px 6px;
  background: var(--border);
}

.account-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--text);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}

.account-item svg {
  color: var(--text-muted);
  flex-shrink: 0;
}

.account-item:hover {
  background: var(--panel-2);
}

.account-item.danger,
.account-item.danger svg {
  color: var(--danger);
}

.account-item.danger:hover {
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}
</style>