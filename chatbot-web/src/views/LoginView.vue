<script setup lang="ts">
import { ref } from 'vue'
import { login } from '@/api'
import { setSession } from '@/auth'

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')

/**
 * 提交登录。成功后只做一件事：setSession()。
 * App.vue 依赖 isAuthenticated（computed），登录态一变就自动把 LoginView 换成主界面，
 * 这里不需要做任何路由跳转 —— 项目没有 vue-router，「页面切换」就是根组件换人。
 */
async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  const name = username.value.trim()
  if (!name || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    const result = await login(name, password.value)
    setSession(result.token, result.user)
  } catch (e) {
    // 后端统一返回「用户名或密码错误」，不区分是账号不存在还是密码错（防用户名枚举）
    error.value = e instanceof Error ? e.message : String(e)
    password.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <h1 class="login-title">Chatbot</h1>
      <p class="login-subtitle">登录后开始对话</p>

      <div v-if="error" class="alert-error login-error">{{ error }}</div>

      <label class="field">
        <span class="field-label">用户名</span>
        <input
          v-model="username"
          class="field-input"
          type="text"
          name="username"
          autocomplete="username"
          placeholder="请输入用户名"
          :disabled="submitting"
          autofocus
        />
      </label>

      <label class="field">
        <span class="field-label">密码</span>
        <input
          v-model="password"
          class="field-input"
          type="password"
          name="password"
          autocomplete="current-password"
          placeholder="请输入密码"
          :disabled="submitting"
        />
      </label>

      <button class="btn-primary login-submit" type="submit" :disabled="submitting">
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <p class="login-hint">初始管理员 admin / admin，登录后请尽快修改密码</p>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: var(--bg);
}

.login-card {
  width: 100%;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 28px 24px 20px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgb(0 0 0 / 8%);
}

.login-title {
  margin: 0;
  font-size: 24px;
  text-align: center;
  color: var(--accent);
}

.login-subtitle {
  margin: -6px 0 4px;
  font-size: 13px;
  text-align: center;
  color: var(--text-muted);
}

.login-submit {
  margin-top: 4px;
  width: 100%;
  padding: 11px;
}

.login-hint {
  margin: 0;
  font-size: 12px;
  text-align: center;
  color: var(--text-muted);
}
</style>