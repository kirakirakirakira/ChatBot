/** 后端 REST 接口返回的会话。 */
export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

/** 后端返回的单条历史消息。 */
export interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  /** 思考过程全文；没开启思考或非推理模型时后端不下发该字段。 */
  reasoning?: string
  /** 生成本条回答用的模型 id；用户消息没有。 */
  model?: string
  /** 输入 token 数（含历史上下文）；拿不到时后端不下发。 */
  promptTokens?: number
  /** 输出 token 数（思考 + 正式回答）。 */
  completionTokens?: number
  /** 其中思考占的输出 token。 */
  reasoningTokens?: number
  createdAt: string
}

/** GET /api/llm/options：模型选择器的数据源。 */
export interface LlmOptions {
  models: string[]
  defaultModel: string
}

/**
 * 后端返回的消息分页（MessagePageVO）。
 * 刻意没有 total/总页数：界面只需要「还能不能往前翻」，算总数要在 LONGTEXT 大表上多跑一次 count(*)。
 */
export interface MessagePage {
  /** 本页消息，已按时间正序，直接渲染，前端不用再反转。 */
  items: Message[]
  /** 加载更早一页时要带的游标（本页最老一条的 id）；null 表示没有更早的了。 */
  beforeId: number | null
  /** 是否还有更早的消息；false 时不显示「加载更早的消息」。 */
  hasMore: boolean
}

/** 后端 SSE 事件（ChatEvent）：reasoning/delta 用 content，done 用 messageId，error 用 content。 */
export interface ChatStreamEvent {
  type: 'reasoning' | 'delta' | 'done' | 'error'
  content?: string
  messageId?: number
  /** done 事件附带：这条回答的模型与用量，前端当场回填，不用等刷新。 */
  model?: string
  prompt_tokens?: number
  completion_tokens?: number
  reasoning_tokens?: number
}

/** 界面渲染用消息：比后端 Message 多了流式生成期间的临时状态。 */
export interface UiMessage {
  /** 数据库 id；流式生成尚未结束时为 null。 */
  id: number | null
  role: 'user' | 'assistant'
  content: string
  /** 思考过程增量，仅展示用，后端不入库。 */
  reasoning?: string
  /** 生成失败时的错误文案。 */
  error?: string
  /** 是否正在流式生成。 */
  streaming?: boolean
  /** 回答用的模型 + 用量，展示在气泡底部。 */
  model?: string
  promptTokens?: number
  completionTokens?: number
  reasoningTokens?: number
}

/** 后端返回的登录用户信息（UserVO）。没有 password 字段，哈希也不出网。 */
export interface CurrentUser {
  id: number
  username: string
  /** 0=普通用户，1=管理员，见后端 com.chatbot.chatbot.auth.Roles。 */
  role: number
  /** 后端给的中文角色名，直接展示，前端不用再维护一份映射。 */
  roleLabel: string
  /** 该用户的系统提示词（人设）；没设过为 null。管理员的用户列表里恒为 null。 */
  systemPrompt?: string | null
  createdAt: string
}

/** 登录 / 改密码成功返回的登录态（LoginResponse）。 */
export interface LoginResult {
  token: string
  tokenType: string
  /** token 有效期（秒）。 */
  expiresIn: number
  user: CurrentUser
}
