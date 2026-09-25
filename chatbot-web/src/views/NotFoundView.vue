<script setup lang="ts">
import { useRoute } from 'vue-router'
import { HOME_PATH } from '@/router/paths'

const route = useRoute()
</script>

<template>
  <div class="status-page">
    <div class="status-card">
      <p class="status-code">404</p>
      <h1 class="status-title">没有这个页面</h1>
      <p class="status-text">{{ route.path }}</p>
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
  font-size: 13px;
  color: var(--text-muted);
  word-break: break-all;
}

.status-action {
  display: inline-flex;
  align-items: center;
  padding: 9px 18px;
  border-radius: var(--radius-s);
  font-size: 14px;
  text-decoration: none;
}
</style>
