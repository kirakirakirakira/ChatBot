<script setup lang="ts">
import { computed } from 'vue'
import ModuleNav from '@/components/ModuleNav.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { currentUser, setSession } from '@/auth'
import type { LoginResult } from '@/types'

/**
 * 管理员重置过密码、本人还没改（UserVO.mustChangePassword）：
 * 在外壳上挂一个**关不掉**的改密框（force 模式），改完才能用任何模块。
 * 挂这里而不是挂在某个页面里：强制改密是「账号级」的事，不该跟着模块走。
 */
const mustChangePassword = computed(() => currentUser.value?.mustChangePassword === true)

function onPasswordChanged(result: LoginResult): void {
  // 后端换发了新 token，必须换上，否则下一个请求就 401
  setSession(result.token, result.user)
}
</script>

<template>
  <!--
    应用外壳：左边一条模块导航，右边是内容区。
    所有「和聊天平级」的功能模块都作为它的子路由挂在下面，因此每个模块天生就有
    同一套导航与登录闸门，新模块不用自己再画一遍外框。
    登录页刻意不在这里面（见 router/routes.ts）。
  -->
  <div class="app-shell">
    <ModuleNav />
    <main class="app-main">
      <RouterView />
    </main>
    <ChangePasswordDialog v-if="mustChangePassword" force @changed="onPasswordChanged" />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  height: 100%;
  background: var(--bg);
}

/* min-width: 0 不能省：flex 子项默认 min-width:auto，
   聊天区里的长代码块会把整行撑宽，把导航条挤出视口。 */
.app-main {
  flex: 1;
  min-width: 0;
  height: 100%;
}
</style>
