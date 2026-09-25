<script setup lang="ts">
import ModuleNav from '@/components/ModuleNav.vue'
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
