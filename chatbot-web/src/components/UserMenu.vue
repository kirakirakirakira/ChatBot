<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { CurrentUser } from '@/types'

const props = defineProps<{
  user: CurrentUser
  isAdmin: boolean
}>()

const emit = defineEmits<{
  users: []
  prompt: []
  password: []
  logout: []
}>()

/**
 * 顶栏只留一个头像按钮，其余入口收进下拉：
 * 四个文字按钮并排会把顶栏变成工具条，聊天界面的顶栏应该几乎隐形。
 */
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function toggle(): void {
  open.value = !open.value
}

function pick(action: () => void): void {
  open.value = false
  action()
}

function onDocumentMouseDown(e: MouseEvent): void {
  if (open.value && root.value && !root.value.contains(e.target as Node)) {
    open.value = false
  }
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    open.value = false
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
</script>

<template>
  <div ref="root" class="user-menu">
    <button class="avatar-btn" type="button" :title="user.username" @click="toggle">
      <span class="avatar">{{ (user.username || '?').charAt(0).toUpperCase() }}</span>
      <svg class="chevron" :class="{ open }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6" /></svg>
    </button>

    <div v-if="open" class="menu" role="menu">
      <div class="menu-head">
        <div class="menu-name">{{ user.username }}</div>
        <div class="menu-role" :class="{ admin: isAdmin }">{{ user.roleLabel }}</div>
      </div>
      <div class="menu-sep" />
      <button v-if="isAdmin" class="menu-item" type="button" role="menuitem" @click="pick(() => emit('users'))">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>
        用户管理
      </button>
      <button class="menu-item" type="button" role="menuitem" @click="pick(() => emit('prompt'))">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3" /><path d="M18.4 5.6 16.3 7.7" /><path d="M21 12h-3" /><path d="M5.6 7.7 7.7 5.6" /><path d="M3 12h3" /><path d="M12 21a9 9 0 0 0 9-9H3a9 9 0 0 0 9 9Z" /></svg>
        系统提示词
      </button>
      <button class="menu-item" type="button" role="menuitem" @click="pick(() => emit('password'))">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" /><path d="M7 11V7a5 5 0 0 1 10 0v4" /></svg>
        修改密码
      </button>
      <div class="menu-sep" />
      <button class="menu-item danger" type="button" role="menuitem" @click="pick(() => emit('logout'))">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" /></svg>
        退出登录
      </button>
    </div>
  </div>
</template>

<style scoped>
.user-menu {
  position: relative;
}

.avatar-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
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

.chevron {
  color: var(--text-muted);
  transition: transform 140ms ease;
}

.chevron.open {
  transform: rotate(180deg);
}

.menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  min-width: 176px;
  padding: 6px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  box-shadow: var(--shadow-2);
  animation: menu-in 130ms cubic-bezier(0.2, 0.9, 0.3, 1);
}

@keyframes menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
}

.menu-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px 6px;
}

.menu-name {
  font-size: 13.5px;
  font-weight: 600;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-role {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 11px;
}

.menu-role.admin {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 35%, transparent);
  background: var(--accent-soft);
}

.menu-sep {
  height: 1px;
  margin: 5px 6px;
  background: var(--border);
}

.menu-item {
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

.menu-item svg {
  color: var(--text-muted);
  flex-shrink: 0;
}

.menu-item:hover {
  background: var(--panel-2);
}

.menu-item.danger,
.menu-item.danger svg {
  color: var(--danger);
}

.menu-item.danger:hover {
  background: color-mix(in srgb, var(--danger) 10%, transparent);
}
</style>
