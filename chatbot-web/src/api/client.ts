/**
 * HTTP 传输层：全站唯一的 fetch 封装，也是唯一的 401 兜底。
 *
 * 为什么从 api.ts 里抽出来：项目正在长出一批「和聊天平级」的功能模块（用户管理，以后可能还有运营台、审计台…），
 * 每个模块该有自己的 api 文件（模块内聚、互不干扰、不会两个人同时改一个 api.ts），
 * 但「挂 Authorization 头」和「401 就清登录态」这两件事只能有一份实现——
 * 否则某个模块自己写了 fetch 又忘了处理 401，界面就会停在一张点什么都 401 的死页面上。
 *
 * 依赖方向刻意单向：client.ts → auth.ts。本文件不认识路由（不 import router / 任何模块的 api 文件），
 * 收到 401 只负责清登录态；「登录态没了就回登录页」由 App.vue 里对 isAuthenticated 的全局 watch 统一处理——
 * 放那儿而不是放这儿，是因为掉登录态的路径不止 401 一条（还有主动退出登录），兜底要兜在一个口子上。
 * 这样也顺带避开了 router → guards → api → client → router 的循环依赖（ESM 下表现为绑定初始化时是 undefined，很难查）。
 */
import { clearSession, token } from '@/auth'

/** 所有后端接口的统一前缀。开发期由 vite.config.ts 把 /api 代理到 :8089，前端代码里不写死后端地址。 */
export const API_BASE = '/api'

/**
 * 普通 REST 请求。非 2xx 时尽量取后端 ErrorResponse 的 message 字段
 * （GlobalExceptionHandler 统一返回 {timestamp,status,error,message,path}）。
 *
 * 所有请求在这里统一挂 Authorization 头、统一处理 401：
 * 401 = 本地 token 过期或被作废（比如刚改过密码、被管理员禁用、或被重置了密码），
 * 清掉登录态之后界面自动弹回登录页。
 * 这就是「所有界面都需要权限，否则跳回登录」的兜底，不用每个页面各写一遍。
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(API_BASE + path, { ...init, headers: withAuth(init?.headers) })
  if (!response.ok) {
    const message = await extractErrorMessage(response)
    if (response.status === 401) {
      clearSession()
    }
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as unknown as T
  }
  return (await response.json()) as T
}

/** 从错误响应里捞后端给的中文 message；响应体不是 JSON（比如网关吐了 HTML）就用状态码兜一句。 */
export async function extractErrorMessage(response: Response): Promise<string> {
  const fallback = `请求失败（HTTP ${response.status}）`
  try {
    const data = (await response.json()) as { message?: string }
    return data.message || fallback
  } catch {
    return fallback
  }
}

/**
 * 给请求头加上 Authorization: Bearer <token>。
 * 用 Headers 对象合并，兼容调用方传进来的各种 HeadersInit 形态。
 * 需要读字节流的接口（附件、SSE）用不了 request()，但照样走这里拿请求头，401 语义才不会分叉。
 */
export function withAuth(headers?: HeadersInit): HeadersInit {
  const merged = new Headers(headers)
  if (token.value) {
    merged.set('Authorization', `Bearer ${token.value}`)
  }
  return merged
}
