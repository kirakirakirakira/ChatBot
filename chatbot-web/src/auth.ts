/**
 * 登录态：token + 当前用户，存 localStorage（刷新页面不掉线）。
 *
 * 刻意做成模块级 ref 而不是某个组件里的局部状态：api.ts 收到 401 时要能直接清掉登录态，
 * App.vue 依赖 isAuthenticated 自动切回登录页 —— 这就是「所有界面都需要权限，
 * 否则跳回登录界面」的兜底，不用每个页面各写一遍判断。
 *
 * 注意：本文件不要 import api.ts，否则会和 api.ts -> auth.ts 形成循环依赖。
 */
import { computed, ref } from 'vue'
import type { CurrentUser } from '@/types'

/** 与后端 com.chatbot.chatbot.auth.Roles 保持一致：0=普通用户，1=管理员，2=超级管理员，3=访客。 */
export const ROLE_USER = 0
export const ROLE_ADMIN = 1
export const ROLE_SUPER_ADMIN = 2
export const ROLE_GUEST = 3

const TOKEN_KEY = 'chatbot.token'
const USER_KEY = 'chatbot.user'

function readStoredUser(): CurrentUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    // 存坏了（手改过、或旧版本留下的格式）就当没有，让用户重新登录
    return null
  }
}

export const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
export const currentUser = ref<CurrentUser | null>(readStoredUser())

/**
 * 本地有 token 就先当作已登录。token 到底还有没有效，
 * 由 App.vue 启动时调 /api/auth/me 确认（过期/被作废会 401，然后 clearSession）。
 */
export const isAuthenticated = computed(() => token.value !== null)
/** 管理端准入：管理员**或超级管理员**（与后端 Roles.canAccessAdmin 同一口径）。 */
export const isAdmin = computed(() => currentUser.value?.role === ROLE_ADMIN || currentUser.value?.role === ROLE_SUPER_ADMIN)

export const isSuperAdmin = computed(() => currentUser.value?.role === ROLE_SUPER_ADMIN)

/**
 * 展示名：昵称优先，没设昵称就回退到登录名。
 *
 * 放这里而不是各组件自己写 `user.nickname || user.username`：顶栏头像、左下角账号菜单、
 * 个人信息页至少要显示三遍，写三遍就一定会有某一处忘了回退（于是界面上出现一个空白名字）。
 * 头像首字母也从它取，昵称改了头像跟着变。
 */
export const displayName = computed(() => {
  const user = currentUser.value
  if (user === null) {
    return ''
  }
  const nickname = user.nickname?.trim()
  return nickname ? nickname : user.username
})

export function setSession(newToken: string, user: CurrentUser): void {
  token.value = newToken
  currentUser.value = user
  localStorage.setItem(TOKEN_KEY, newToken)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 退出登录、或任何接口返回 401 时调用：清空登录态，界面立刻回到登录页。 */
export function clearSession(): void {
  token.value = null
  currentUser.value = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
