<script setup lang="ts">
import { currentUser } from '@/auth'
import { HOME_PATH } from '@/router/paths'
</script>

<template>
  <div class="status-page">
    <div class="status-card">
      <p class="status-code">403</p>
      <h1 class="status-title">这个模块需要管理员权限</h1>
      <p class="status-text">
        当前账号的角色是「{{ currentUser?.roleLabel ?? '未知' }}」。如果认为这是误判，
        请让管理员在用户管理里调整你的角色。
      </p>
      <RouterLink class="btn-primary status-action" :to="HOME_PATH">回到聊天</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.status-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.status-card {
  max-width: 380px;
  text-align: center;
}

.status-code {
  margin: 0;
  font-size: 46px;
  font-weight: 700;
  line-height: 1;
  color: var(--border-strong);
}

.status-title {
  margin: 10px 0 6px;
  font-size: 17px;
}

.status-text {
  margin: 0 0 18px;
  font-size: 13.5px;
  color: var(--text-muted);
}

/* .btn-primary 是给 <button> 写的（没有 padding / display），套在 RouterLink 上要自己补 */
.status-action {
  display: inline-flex;
  align-items: center;
  padding: 9px 18px;
  border-radius: var(--radius-s);
  font-size: 14px;
  text-decoration: none;
}
</style>
