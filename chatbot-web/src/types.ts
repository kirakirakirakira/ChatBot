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
  createdAt: string
}

/** 后端 SSE 事件（ChatEvent）：reasoning/delta 用 content，done 用 messageId，error 用 content。 */
export interface ChatStreamEvent {
  type: 'reasoning' | 'delta' | 'done' | 'error'
  content?: string
  messageId?: number
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
}