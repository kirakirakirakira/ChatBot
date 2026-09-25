<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { clearSession, currentUser, isAdmin } from '@/auth'

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
 * 导航条底部的账号按钮 + 退出登录。
 * 为什么放在外壳而不是各模块里：管理页没有聊天顶栏的头像菜单，若退出登录只存在于
 * ChatView，站在 /admin/users 上的人就得先跳回聊天才能退出——外壳级的事不该跟着模块走。
 * 这里只调 clearSession()：跳回登录页由 App.vue 的全局 watch 统一负责。
 */
const menuOpen = ref(false)
const accountRoot = ref<HTMLElement | null>(null)

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
  <nav class="module-nav" aria-label="功能模块">
    <!-- 激活态直接用 vue-router 自带的 router-link-active（含子路由匹配），不用自己算 -->
    <RouterLink
      v-for="item in items"
      :key="item.path"
      class="nav-item"
      :to="item.path"
      :title="item.title"
      :aria-label="item.title"
    >
      <component :is="item.icon" />
    </RouterLink>

    <div class="nav-spacer" />

    <div ref="accountRoot" class="nav-account">
      <button
        class="avatar-btn"
        type="button"
        :title="currentUser?.username ?? ''"
        :aria-label="currentUser?.username ?? ''"
        @click="menuOpen = !menuOpen"
      >
        <span class="avatar">{{ (currentUser?.username ?? '?').charAt(0).toUpperCase() }}</span>
      </button>
      <div v-if="menuOpen" class="account-menu" role="menu">
        <div class="account-head">
          <div class="account-name">{{ currentUser?.username }}</div>
          <div class="account-role">{{ currentUser?.roleLabel }}</div>
        </div>
        <div class="account-sep" />
        <button class="account-item" type="button" role="menuitem" @click="logout">退出登录</button>
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
  padding: 10px 0;
  background: var(--panel-2);
  border-right: 1px solid var(--border);
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: var(--radius-m);
  color: var(--text-muted);
  transition: background 120ms ease, color 120ms ease;
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
}

.avatar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  cursor: pointer;
}

.avatar-btn:hover {
  background: color-mix(in srgb, var(--text) 7%, transparent);
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-pill);
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 55%, #2b6cb0));
  color: #fff;
  font-size: 13px;
  font-weight: 650;
}

.account-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 40;
  min-width: 150px;
  padding: 6px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  box-shadow: var(--shadow-2);
}

.account-head {
  padding: 6px 10px 4px;
}

.account-name {
  font-size: 13.5px;
  font-weight: 600;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-role {
  font-size: 11px;
  color: var(--text-muted);
}

.account-sep {
  height: 1px;
  margin: 5px 6px;
  background: var(--border);
}

.account-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--danger);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}

.account-item:hover {
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}
</style>
