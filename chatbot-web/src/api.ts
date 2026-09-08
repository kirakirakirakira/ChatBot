import type { ChatStreamEvent, Conversation, CurrentUser, LoginResult, Message } from '@/types'
import { clearSession, token } from '@/auth'

const BASE = '/api'

/**
 * 普通 REST 请求。非 2xx 时尽量取后端 ErrorResponse 的 message 字段
 * （GlobalExceptionHandler 统一返回 {timestamp,status,error,message,path}）。
 *
 * 所有请求在这里统一挂 Authorization 头、统一处理 401：
 * 401 = 本地 token 过期或被作废（比如刚改过密码），清掉登录态之后
 * App.vue 里的 isAuthenticated 变 false，界面自动弹回登录页。
 * 这就是「所有界面都需要权限，否则跳回登录」的兜底，不用每个页面各写一遍。
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, { ...init, headers: withAuth(init?.headers) })
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

async function extractErrorMessage(response: Response): Promise<string> {
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
 */
function withAuth(headers?: HeadersInit): HeadersInit {
  const merged = new Headers(headers)
  if (token.value) {
    merged.set('Authorization', `Bearer ${token.value}`)
  }
  return merged
}

/** 登录：全站唯一不需要 token 的接口。成功后由调用方 setSession()。 */
export function login(username: string, password: string): Promise<LoginResult> {
  return request<LoginResult>('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

/** 当前登录用户。前端启动时用它确认 localStorage 里的 token 是否还有效。 */
export function fetchMe(): Promise<CurrentUser> {
  return request<CurrentUser>('/auth/me')
}

/**
 * 修改自己的密码。后端会换发一个新 token（旧 token 的签发时间早于
 * password_changed_at，已经作废），所以调用方拿到结果必须 setSession()，
 * 否则下一个请求就 401 了。
 */
export function changePassword(oldPassword: string, newPassword: string): Promise<LoginResult> {
  return request<LoginResult>('/users/me/password', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

/** 用户列表，仅管理员（后端 @RequireAdmin；普通用户会拿到 403）。 */
export function listUsers(): Promise<CurrentUser[]> {
  return request<CurrentUser[]>('/users')
}

export function createConversation(): Promise<Conversation> {
  return request<Conversation>('/conversations', { method: 'POST' })
}

export function listConversations(): Promise<Conversation[]> {
  return request<Conversation[]>('/conversations')
}

export function getMessages(conversationId: number): Promise<Message[]> {
  return request<Message[]>(`/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId: number): Promise<void> {
  return request<void>(`/conversations/${conversationId}`, { method: 'DELETE' })
}

export interface StreamHandlers {
  /** 思考过程增量（推理模型 + 开启思考时才有）。 */
  onReasoning?: (text: string) => void
  /** 正式回答增量。 */
  onDelta: (text: string) => void
  /** 本轮生成结束，messageId 是入库后的助手消息 id。 */
  onDone?: (messageId?: number) => void
  /** 后端通过 SSE error 事件报出的生成错误。 */
  onError?: (message: string) => void
}

/**
 * 发消息并以 SSE 流式接收回复。
 *
 * POST + text/event-stream 用不了 EventSource（它只支持 GET），
 * 所以用 fetch 读响应流，手动按 SSE 帧解析：事件之间以空行分隔，
 * 数据行形如 data:{"type":"delta","content":"你"}。
 *
 * @param enableThinking 请求级思考开关：true/false 只对本条消息生效；
 *                       传 undefined 则不下发该字段，由服务端 llm.enable-thinking 配置决定。
 * @param signal 传入 AbortController.signal，abort() 即「停止生成」，
 *               fetch 会抛 AbortError，后端检测到断开后保留已生成部分。
 */
export async function streamChat(
  conversationId: number,
  message: string,
  enableThinking: boolean | undefined,
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const body: Record<string, unknown> = { message }
  if (enableThinking !== undefined) {
    body.enableThinking = enableThinking
  }

  const response = await fetch(`${BASE}/conversations/${conversationId}/chat`, {
    method: 'POST',
    headers: withAuth({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok || !response.body) {
    // 还没升级成 SSE 就失败（如 401 未登录、404 会话不存在、400 参数校验）：响应体是 JSON 错误
    const message = await extractErrorMessage(response)
    if (response.status === 401) {
      clearSession()
    }
    throw new Error(message)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    // stream: true —— 多字节字符（中文/emoji）可能被切在两个网络包之间，
    // 让 decoder 自己缓存半个字符，不能每次 new 一个 decoder。
    buffer += decoder.decode(value, { stream: true })

    let match = /\r?\n\r?\n/.exec(buffer)
    while (match) {
      const rawEvent = buffer.slice(0, match.index)
      buffer = buffer.slice(match.index + match[0].length)
      dispatchEvent(rawEvent, handlers)
      match = /\r?\n\r?\n/.exec(buffer)
    }
  }
  // 流结束时缓冲区里还剩最后一个没跟空行的事件（服务端关闭连接的边界情况）
  if (buffer.trim()) {
    dispatchEvent(buffer, handlers)
  }
}

function dispatchEvent(rawEvent: string, handlers: StreamHandlers): void {
  const data = rawEvent
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))
    .join('\n')
  if (!data) {
    return
  }
  let event: ChatStreamEvent
  try {
    event = JSON.parse(data) as ChatStreamEvent
  } catch {
    return // 不完整的帧直接忽略，等下一个事件
  }
  switch (event.type) {
    case 'reasoning':
      handlers.onReasoning?.(event.content ?? '')
      break
    case 'delta':
      handlers.onDelta(event.content ?? '')
      break
    case 'done':
      handlers.onDone?.(event.messageId)
      break
    case 'error':
      handlers.onError?.(event.content ?? '未知错误')
      break
  }
}