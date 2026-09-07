import type { ChatStreamEvent, Conversation, Message } from '@/types'

const BASE = '/api'

/**
 * 普通 REST 请求。非 2xx 时尽量取后端 ErrorResponse 的 message 字段
 * （GlobalExceptionHandler 统一返回 {timestamp,status,error,message,path}）。
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, init)
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response))
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
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok || !response.body) {
    // 还没升级成 SSE 就失败（如 404 会话不存在、400 参数校验）：响应体是 JSON 错误
    throw new Error(await extractErrorMessage(response))
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