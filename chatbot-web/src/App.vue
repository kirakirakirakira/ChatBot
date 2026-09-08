<script setup lang="ts">
import { onMounted, ref } from 'vue'
import LoginView from '@/views/LoginView.vue'
import ChatView from '@/views/ChatView.vue'
import { clearSession, isAuthenticated, setSession, token } from '@/auth'
import { fetchMe } from '@/api'

/**
 * 根组件就是权限闸门：本项目没有 vue-router，「页面切换」= 换掉根组件。
 * 未登录（isAuthenticated=false）时只可能渲染 LoginView，
 * 因此不存在任何绕过登录直接看到业务界面的路径。
 */

/** 本地有 token 时先别急着渲染主界面，等 /api/auth/me 确认它还没过期。 */
const restoring = ref(token.value !== null)

onMounted(async () => {
  if (!token.value) {
    restoring.value = false
    return
  }
  try {
    const me = await fetchMe()
    setSession(token.value, me) // 顺带刷新 localStorage 里的用户信息（角色可能变了）
  } catch {
    // token 过期/被作废/用户被删：api.ts 收到 401 已经清过登录态，这里再兜一次底
    clearSession()
  } finally {
    restoring.value = false
  }
})
</script>

<template>
  <div v-if="restoring" class="restoring">正在恢复登录状态…</div>
  <ChatView v-else-if="isAuthenticated" />
  <LoginView v-else />
</template>

<style scoped>
.restoring {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 14px;
  background: var(--bg);
}
</style>