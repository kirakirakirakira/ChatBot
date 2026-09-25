<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { isAdmin } from '@/auth'

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
</style>
