<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api'
import { setSession } from '@/auth'
import { safeNextPath } from '@/router/guards'
import { HOME_PATH } from '@/router/paths'

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')

/**
 * 提交登录。成功后 setSession() 写登录态，再跳回「被守卫拦下来之前想去的那个模块」。
 * next 是 URL 上的外部可控参数，**必须过 safeNextPath()**：不校验就是一个开放重定向。
 * 用 replace 而不是 push：登录页不该留在历史记录里，否则登录后按后退又回到登录页
 * （守卫会再把你弹回首页，表现为「后退按不动」）。
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
    void router.replace(safeNextPath(route.query.next) ?? HOME_PATH)
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
      <div class="login-brand">
        <span class="brand-mark" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3" /><path d="M18.4 5.6 16.3 7.7" /><path d="M21 12h-3" /><path d="M5.6 7.7 7.7 5.6" /><path d="M3 12h3" /><path d="M12 21a9 9 0 0 0 9-9H3a9 9 0 0 0 9 9Z" /></svg>
        </span>
        <h1 class="login-title">Chatbot</h1>
      </div>
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
/* 背景用两团很淡的品牌色光晕：纯灰底太「后台管理系统」，光晕让登录页有一点产品感 */
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background:
    radial-gradient(600px 320px at 15% 8%, color-mix(in srgb, var(--accent) 10%, transparent), transparent 70%),
    radial-gradient(520px 300px at 88% 92%, color-mix(in srgb, #2b6cb0 9%, transparent), transparent 70%),
    var(--bg);
}

.login-card {
  width: 100%;
  max-width: 372px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 32px 28px 22px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 20px;
  box-shadow: var(--shadow-2);
  animation: card-in 220ms cubic-bezier(0.2, 0.9, 0.3, 1);
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
  }
}

.login-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 50%, #2b6cb0));
  color: #fff;
  box-shadow: var(--shadow-1);
}

.login-title {
  margin: 0;
  font-size: 23px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.login-subtitle {
  margin: -6px 0 6px;
  font-size: 13px;
  text-align: center;
  color: var(--text-muted);
}

.login-submit {
  margin-top: 6px;
  width: 100%;
  padding: 11px;
  border-radius: var(--radius-m);
  font-size: 15px;
}

.login-hint {
  margin: 2px 0 0;
  font-size: 12px;
  text-align: center;
  color: var(--text-muted);
  opacity: 0.85;
}
</style>
