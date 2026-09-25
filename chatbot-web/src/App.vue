<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isAuthenticated } from '@/auth'
import { LOGIN_PATH } from '@/router/paths'

/**
 * 根组件只做两件事，页面本身全交给路由：
 *
 * 1. 首次导航完成前显示「正在恢复登录状态…」。守卫里的 ensureSession() 是异步的，
 *    那之前 RouterView 什么都不渲染，不给占位就是一片白屏。
 * 2. 全站唯一的「掉登录态就回登录页」兜底：不管原因是接口 401、用户点了退出登录，
 *    还是账号被管理员禁用，isAuthenticated 一变 false 就把界面送回登录页。
 *
 * 第 2 条刻意放这里而不是 api/client.ts：掉登录态的入口不止 401 一个
 * （ChatView 的 logout() 是直接调 clearSession() 的），兜底要兜在一个口子上；
 * 顺带的好处是 ChatView 不用为了接入路由改任何一行。
 *
 * 「未登录不能看业务界面」的保证现在在 router/guards.ts 里（原来是本文件的三分支条件渲染）。
 */
const router = useRouter()
const route = useRoute()
const ready = ref(false)

onMounted(async () => {
  await router.isReady()
  ready.value = true
})

watch(isAuthenticated, (ok) => {
  // 已经在登录页就别再 replace 一次：会覆盖掉 ?next=，还会触发重复导航告警
  if (ok || !ready.value || route.meta.public === true) {
    return
  }
  void router.replace({ path: LOGIN_PATH, query: { next: route.fullPath } })
})
</script>

<template>
  <div v-if="!ready" class="restoring">正在恢复登录状态…</div>
  <RouterView v-else />
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
