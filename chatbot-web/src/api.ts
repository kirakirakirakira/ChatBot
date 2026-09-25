/**
 * 聊天与登录相关的后端调用。本文件只描述「有哪些接口、请求体和响应长什么样」；
 * fetch 封装、Authorization 头、401 兜底全在 @/api/client（全站只有一份）。
 * 以后新增别的平级模块，请另开 src/api/<模块>.ts，不要往这里堆。
 */
import type {
  Attachment,
  ChatStreamEvent,
  Conversation,
  CurrentUser,
  LlmOptions,
  LoginResult,
  MessagePage,
} from '@/types'
import { clearSession } from '@/auth'
import { API_BASE, extractErrorMessage, request, withAuth } from '@/api/client'

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

/**
 * 改自己的系统提示词（人设），每轮对话都会作为 system 消息放在历史最前面。
 * 传全空白 = 清除。返回更新后的用户信息，调用方用它覆盖本地登录态里的 currentUser。
 */
export function updateSystemPrompt(systemPrompt: string): Promise<CurrentUser> {
  return request<CurrentUser>('/users/me/system-prompt', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ systemPrompt }),
  })
}

export function createConversation(): Promise<Conversation> {
  return request<Conversation>('/conversations', { method: 'POST' })
}

export function listConversations(): Promise<Conversation[]> {
  return request<Conversation[]>('/conversations')
}

/** 消息分页查询参数。 */
export interface MessagePageQuery {
  /** 游标：只取 id 小于它的消息，值来自上一页响应的 beforeId。不传就是最新一页。 */
  before?: number | null
  /** 单页条数。后端默认 50、上限 200（超出按上限截断，不报 400）。 */
  limit?: number
}

/**
 * 取某个会话的消息，**从最新往前翻**。
 * 返回的 items 已经是时间正序；要继续往前翻就把响应里的 beforeId 当 before 传回来。
 * 用 id 游标而不是页码：一边翻页一边有新消息入库时，offset 分页会重复或漏消息。
 */
export function getMessages(conversationId: number, query?: MessagePageQuery): Promise<MessagePage> {
  const params = new URLSearchParams()
  if (query?.before != null) {
    params.set('before', String(query.before))
  }
  if (query?.limit != null) {
    params.set('limit', String(query.limit))
  }
  const qs = params.toString()
  return request<MessagePage>(`/conversations/${conversationId}/messages${qs ? `?${qs}` : ''}`)
}

/**
 * 重命名会话。返回更新后的会话对象，调用方直接覆盖本地那一条即可。
 * 后端刻意不刷新 updated_at：改名不是「活动」，不该把会话顶到列表最前面。
 */
export function renameConversation(conversationId: number, title: string): Promise<Conversation> {
  return request<Conversation>(`/conversations/${conversationId}/title`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
}

export function deleteConversation(conversationId: number): Promise<void> {
  return request<void>(`/conversations/${conversationId}`, { method: 'DELETE' })
}

/** 模型选项：界面选择器的数据源，来自配置 llm.available-models / llm.vision-models。 */
export function fetchLlmOptions(): Promise<LlmOptions> {
  return request<LlmOptions>('/llm/options')
}

/**
 * 上传一张图片，返回它的 id（发消息时放进 attachmentIds）。
 * 不手动设 Content-Type：FormData 的 multipart 边界由浏览器生成，写死了反而会让后端解析失败。
 */
export function uploadAttachment(conversationId: number, file: File): Promise<Attachment> {
  const form = new FormData()
  form.append('file', file)
  return request<Attachment>(`/conversations/${conversationId}/attachments`, { method: 'POST', body: form })
}

/**
 * 取附件字节并转成本地 objectURL。
 * 为什么不能直接 <img src="/api/attachments/1">：img 标签带不了 Authorization 头，
 * 而后端刻意不做 ?token= 兜底（那等于把长期凭证写进 URL、日志和浏览器历史）。
 * 调用方负责在不用时 URL.revokeObjectURL()。
 */
export async function fetchAttachmentUrl(attachmentId: number): Promise<string> {
  const response = await fetch(`${API_BASE}/attachments/${attachmentId}`, { headers: withAuth() })
  if (!response.ok) {
    const message = await extractErrorMessage(response)
    if (response.status === 401) {
      clearSession()
    }
    throw new Error(message)
  }
  return URL.createObjectURL(await response.blob())
}

/**
 * 单次生成的可调项。三个都可选：
 * enableThinking 不传 = 用服务端 llm.enable-thinking；model 不传 = 用服务端 llm.model；
 * thinkingBudget 不传 = 不下发 thinking_budget（思考关着时后端也会忽略它）。
 */
export interface StreamOptions {
  enableThinking?: boolean
  model?: string
  thinkingBudget?: number | null
  /** 联网搜索：按次计费，所以由界面显式开关控制，不给默认值。 */
  enableSearch?: boolean
  /** 随本条消息发送的图片附件 id；不带 = 纯文本消息。重新生成不需要它（图片已经挂在原用户消息上）。 */
  attachmentIds?: number[]
}

/** done 事件带回来的用量，字段名与后端 JSON 一致（下划线）。 */
export interface MessageUsage {
  model?: string
  prompt_tokens?: number
  completion_tokens?: number
  reasoning_tokens?: number
}

export interface StreamHandlers {
  /** 思考过程增量（推理模型 + 开启思考时才有）。 */
  onReasoning?: (text: string) => void
  /** 正式回答增量。 */
  onDelta: (text: string) => void
  /** 本轮生成结束，messageId 是入库后的助手消息 id；usage 是这条回答的模型与 token 用量。 */
  onDone?: (messageId?: number, usage?: MessageUsage) => void
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
  options: StreamOptions,
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const body: Record<string, unknown> = { message, ...pickStreamOptions(options) }

  const response = await fetch(`${API_BASE}/conversations/${conversationId}/chat`, {
    method: 'POST',
    headers: withAuth({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok || !response.body) {
    await throwIfNotOk(response)
  }
  await consumeSse(response, handlers)
}

/**
 * 重新生成最后一条回答。SSE 契约与 streamChat 完全一致，差别只在端点和请求体：
 * 后端先删掉最后一条助手消息，再用它前面那条用户消息重跑，所以历史不会攒出两份回答。
 *
 * @param enableThinking 语义同 streamChat：undefined = 用服务端 llm.enable-thinking 配置。
 */
export async function streamRegenerate(
  conversationId: number,
  options: StreamOptions,
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  const body: Record<string, unknown> = { ...pickStreamOptions(options) }
  const response = await fetch(`${API_BASE}/conversations/${conversationId}/regenerate`, {
    method: 'POST',
    headers: withAuth({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok || !response.body) {
    await throwIfNotOk(response)
  }
  await consumeSse(response, handlers)
}

/** 还没升级成 SSE 就失败（401 / 404 / 400 等）：响应体是 JSON 错误，走统一的错误提取。 */
async function throwIfNotOk(response: Response): Promise<never> {
  const message = await extractErrorMessage(response)
  if (response.status === 401) {
    clearSession()
  }
  throw new Error(message)
}

/** 把可选项里「给了值」的字段挑进请求体；没给的不下发，让服务端用配置默认值。 */
function pickStreamOptions(options: StreamOptions): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  if (options.enableThinking !== undefined) {
    body.enableThinking = options.enableThinking
  }
  if (options.model) {
    body.model = options.model
  }
  if (options.thinkingBudget != null) {
    body.thinkingBudget = options.thinkingBudget
  }
  if (options.enableSearch !== undefined) {
    body.enableSearch = options.enableSearch
  }
  if (options.attachmentIds && options.attachmentIds.length > 0) {
    body.attachmentIds = options.attachmentIds
  }
  return body
}

/** 读 SSE 流并按帧分发。chat 与 regenerate 共用同一套解析。 */
async function consumeSse(response: Response, handlers: StreamHandlers): Promise<void> {
  const body = response.body
  if (!body) {
    throw new Error('响应没有可读的流')
  }
  const reader = body.getReader()
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
      handlers.onDone?.(event.messageId, {
        model: event.model,
        prompt_tokens: event.prompt_tokens,
        completion_tokens: event.completion_tokens,
        reasoning_tokens: event.reasoning_tokens,
      })
      break
    case 'error':
      handlers.onError?.(event.content ?? '未知错误')
      break
  }
}