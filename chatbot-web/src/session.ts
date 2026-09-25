import { fetchMe } from '@/api'
import { clearSession, setSession, token } from '@/auth'

/**
 * 「本地这个 token 还有效吗」的一次性确认。
 *
 * 原来是 App.vue 的 onMounted 干的（restoring ref + fetchMe），换路由后必须在**守卫**里等它，
 * 否则守卫读到的是 localStorage 里的旧用户信息：管理员被降级成普通用户后，刷新页面那一下
 * requiresAdmin 会误判通过。
 *
 * 只发一次请求并缓存 promise：每次导航都会进守卫，不能一次导航打一次 /api/auth/me。
 *
 * 本文件刻意独立于 auth.ts —— auth.ts 不能 import api.ts（会和 api.ts → auth.ts 成环），
 * 而这里需要同时用到两边，所以单独放一层。
 */
let verifyPromise: Promise<void> | null = null

export function ensureSession(): Promise<void> {
  const currentToken = token.value
  if (currentToken === null) {
    // 本地压根没有 token：不用问后端，直接是未登录状态
    return Promise.resolve()
  }
  verifyPromise ??= (async () => {
    try {
      const me = await fetchMe()
      // 顺带刷新 localStorage 里的用户信息（角色可能已经变了）
      setSession(currentToken, me)
    } catch {
      // token 过期 / 被作废 / 账号被禁用或删掉：api/client.ts 收到 401 已经清过登录态，这里再兜一次底
      clearSession()
    }
  })()
  return verifyPromise
}
