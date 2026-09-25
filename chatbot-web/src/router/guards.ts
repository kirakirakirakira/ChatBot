import type { Router } from 'vue-router'
import { isAdmin, isAuthenticated } from '@/auth'
import { ensureSession } from '@/session'
import { FORBIDDEN_PATH, HOME_PATH, LOGIN_PATH } from './paths'

/**
 * 登录闸门 + 角色闸门。
 *
 * 原来是 App.vue 用三分支条件渲染实现的（未登录时只可能渲染 LoginView，所以绕不过去）；
 * 换成路由后**必须搬到守卫里**，否则「直接在地址栏输 URL」就是一条绕过登录的现成路径。
 */
export function installGuards(router: Router): void {
  router.beforeEach(async (to) => {
    // 本地有 token 时先确认它还有效：后端可能已经改了角色、禁用或删掉了这个账号。
    // ensureSession() 内部只发一次请求，之后的导航直接复用结果。
    // 顺序不能颠倒——守卫若直接读 localStorage 里的旧角色，
    // 「管理员被降级后刷新页面」那一下 requiresAdmin 会误判通过。
    await ensureSession()

    if (to.meta.public === true) {
      // 已登录的人还停在登录页（登录成功后点了浏览器后退）→ 送回业务首页
      return isAuthenticated.value && to.path === LOGIN_PATH ? HOME_PATH : true
    }
    if (!isAuthenticated.value) {
      // 带上 next：登录成功后回到原来想去的模块，而不是永远落在 /chat
      return { path: LOGIN_PATH, query: { next: to.fullPath } }
    }
    if (to.meta.requiresAdmin === true && !isAdmin.value) {
      return FORBIDDEN_PATH
    }
    return true
  })

  router.afterEach((to) => {
    document.title = to.meta.title ? to.meta.title + ' · Chatbot' : 'Chatbot'
  })
}

/**
 * 校验登录后回跳目标。只接受站内绝对路径。
 *
 * 必须挡掉 '//evil.com'（协议相对 URL，浏览器会当外链跳走）和 'https://evil.com'，
 * 否则 ?next= 就是一个现成的开放重定向漏洞——它还是攻击者最容易想到的那个。
 */
export function safeNextPath(raw: unknown): string | null {
  if (typeof raw !== 'string') {
    return null
  }
  if (!raw.startsWith('/') || raw.startsWith('//')) {
    return null
  }
  return raw
}
