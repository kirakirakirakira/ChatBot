<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ConversationSidebar from '@/components/ConversationSidebar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import UserListDialog from '@/components/UserListDialog.vue'
import type { LoginResult, UiMessage } from '@/types'
import type { StreamHandlers, StreamOptions } from '@/api'
import { clearSession, currentUser, isAdmin, setSession } from '@/auth'
import {
  createConversation,
  deleteConversation,
  fetchLlmOptions,
  getMessages,
  renameConversation,
  listConversations,
  streamChat,
  streamRegenerate,
} from '@/api'

const conversations = ref<Awaited<ReturnType<typeof listConversations>>>([])
const activeId = ref<number | null>(null)
const messages = ref<UiMessage[]>([])
const input = ref('')
const streaming = ref(false)
const loadingMessages = ref(false)
/** 消息分页：还有没有更早的、往前翻的游标、以及「加载更早」按钮自己的 loading。 */
const hasMoreOlder = ref(false)
const olderCursor = ref<number | null>(null)
const loadingOlder = ref(false)
/** 单页条数，和后端 ConversationService.DEFAULT_PAGE_SIZE 保持一致（后端上限 200）。 */
const PAGE_SIZE = 50
/** 思考开关：显式传 true/false（每条消息生效）。想改用服务端默认配置，把它设为 undefined 传给 api 即可。 */
const thinkingOn = ref(true)
/** 模型选择器：清单来自后端 /api/llm/options（配置 llm.available-models），选中值存 localStorage。 */
const llmModels = ref<string[]>([])
const selectedModel = ref('')
/**
 * 思考强度档位，值是思维链 token 上限（Chat Completions 的 thinking_budget）；'' = 不限，不下发该参数、用模型默认。
 * 档位对齐百炼官方 reasoning_effort 的映射（low=4096 / medium=16384），「深入」取 131072：
 * 它既是 qwen3.8 系的默认值，也正好是 qwen3.6-flash 的最大思维链长度，对全部可选模型都合法。
 * 再往上（262144 = xhigh）只有 qwen3.8 系吃得下，qwen3.6-flash 会返回 400，所以不放进档位。
 */
const thinkingBudgetSel = ref('')
const THINKING_BUDGET_CHOICES: { value: string; label: string }[] = [
  { value: '', label: '思考强度：不限' },
  { value: '4096', label: '精简 4k' },
  { value: '16384', label: '均衡 16k' },
  { value: '131072', label: '深入 128k' },
]

watch(selectedModel, (v) => localStorage.setItem('chatbot.model', v))
watch(thinkingBudgetSel, (v) => localStorage.setItem('chatbot.thinkingBudget', v))
/** 会话列表加载失败、删除失败这类全局错误，横幅展示。 */
const fatalError = ref('')
const showPasswordDialog = ref(false)
/** 用户管理弹窗：只有管理员看得到入口，普通用户点了也会被后端 403 挡住。 */
const showUserDialog = ref(false)

/** 新建对话的前提：没有选中对话，或选中的对话已经有消息。防止连点攒出一排空对话。 */
const canCreateConversation = computed(() => activeId.value === null || messages.value.length > 0)

/** 后端建会话时写死的默认标题；ChatService 在首条消息后会把它改成消息前 30 字。 */
const DEFAULT_TITLE = '新的对话'

/**
 * 当前选中的对话是不是「一条消息都没有」的空对话。
 * 只在历史加载成功后才更新：加载中或加载失败时保持 false，
 * 否则会把「还没读出来」的对话误判成空对话删掉。
 */
const activeIsEmpty = ref(false)

let abortController: AbortController | null = null
const scroller = ref<HTMLElement | null>(null)

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

function scrollToBottom(): void {
  nextTick(() => {
    const el = scroller.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 退出登录：先掐断可能还在跑的 SSE，再清登录态，App.vue 会立刻换回登录页。 */
function logout(): void {
  stopStreaming()
  clearSession()
}

/** 改密码成功后换上后端换发的新 token，否则下一个请求就会 401 被踢回登录页。 */
function onPasswordChanged(result: LoginResult): void {
  setSession(result.token, result.user)
}

/**
 * 拉模型清单并恢复上次的选择。存过的模型若已不在白名单里（配置改了），回落到服务端默认，
 * 而不是把一个后端会 400 的 id 留在界面上。
 */
async function loadLlmOptions(): Promise<void> {
  try {
    const options = await fetchLlmOptions()
    llmModels.value = options.models
    const stored = localStorage.getItem('chatbot.model')
    const fallback = options.models.includes(options.defaultModel)
      ? options.defaultModel
      : (options.models[0] ?? '')
    selectedModel.value = stored && options.models.includes(stored) ? stored : fallback
    const storedBudget = localStorage.getItem('chatbot.thinkingBudget')
    thinkingBudgetSel.value = THINKING_BUDGET_CHOICES.some((c) => c.value === storedBudget)
      ? (storedBudget ?? '')
      : ''
  } catch (e) {
    fatalError.value = errMsg(e)
  }
}

/** 本轮生成的可调项。思考关着时预算不传：后端同样会忽略，但少传一个不生效的字段更干净。 */
function streamOptions(): StreamOptions {
  return {
    enableThinking: thinkingOn.value,
    model: selectedModel.value || undefined,
    thinkingBudget: thinkingOn.value && thinkingBudgetSel.value ? Number(thinkingBudgetSel.value) : undefined,
  }
}

async function loadConversations(): Promise<void> {
  try {
    conversations.value = await listConversations()
    fatalError.value = ''
  } catch (e) {
    fatalError.value = errMsg(e)
  }
}

async function selectConversation(id: number): Promise<void> {
  if (id === activeId.value) {
    return
  }
  stopStreaming()
  // 先记下「要离开的那个对话是不是空的」：activeId 和 messages 一被覆盖就查不到了
  const leavingId = activeId.value
  const leavingWasEmpty = leavingId !== null && activeIsEmpty.value
  activeId.value = id
  activeIsEmpty.value = false
  loadingMessages.value = true
  resetPaging()
  try {
    // 只取最新一页，更早的靠「加载更早的消息」按需翻，不再一次性把整段历史拉下来渲染
    const page = await getMessages(id, { limit: PAGE_SIZE })
    // reasoning / model / 用量都从库里读回来：刷新后仍能展开思考、看到这条回答是谁花的钱
    messages.value = page.items.map((m) => ({
      id: m.id,
      role: m.role,
      content: m.content,
      reasoning: m.reasoning,
      model: m.model,
      promptTokens: m.promptTokens,
      completionTokens: m.completionTokens,
      reasoningTokens: m.reasoningTokens,
    }))
    hasMoreOlder.value = page.hasMore
    olderCursor.value = page.beforeId
    activeIsEmpty.value = page.items.length === 0
    fatalError.value = ''
    scrollToBottom()
  } catch (e) {
    messages.value = []
    fatalError.value = errMsg(e)
  } finally {
    loadingMessages.value = false
  }
  // 切换成功后再清理空对话：切换失败时用户还停在原对话上，不能把它删了
  if (leavingWasEmpty && leavingId !== null) {
    await discardEmptyConversation(leavingId)
  }
}

function resetPaging(): void {
  hasMoreOlder.value = false
  olderCursor.value = null
  loadingOlder.value = false
}

/**
 * 往前翻一页历史，插到列表最前面。
 *
 * 必须保住滚动位置：往顶部插内容会把视口「顶」下去，用户正在读的那条消息就跑了。
 * 做法是记下插入前的 scrollHeight，插入后把高度差补回 scrollTop，视觉上等于没动。
 *
 * 生成期间禁用：流式增量一直在往底部追加，这时候往顶部插 50 条会把滚动补偿算歪。
 */
async function loadOlder(): Promise<void> {
  const id = activeId.value
  if (id === null || olderCursor.value === null) {
    return
  }
  if (loadingOlder.value || streaming.value || loadingMessages.value) {
    return
  }
  const el = scroller.value
  const prevHeight = el?.scrollHeight ?? 0
  const prevTop = el?.scrollTop ?? 0
  loadingOlder.value = true
  try {
    const page = await getMessages(id, { before: olderCursor.value, limit: PAGE_SIZE })
    // 等待期间用户可能已经切到别的会话，这一页属于旧会话，直接丢掉
    if (activeId.value !== id) {
      return
    }
    const older = page.items.map((m) => ({
      id: m.id,
      role: m.role,
      content: m.content,
      reasoning: m.reasoning,
      model: m.model,
      promptTokens: m.promptTokens,
      completionTokens: m.completionTokens,
      reasoningTokens: m.reasoningTokens,
    }))
    messages.value = [...older, ...messages.value]
    hasMoreOlder.value = page.hasMore
    olderCursor.value = page.beforeId
    await nextTick()
    if (el) {
      el.scrollTop = prevTop + (el.scrollHeight - prevHeight)
    }
    fatalError.value = ''
  } catch (e) {
    fatalError.value = errMsg(e)
  } finally {
    loadingOlder.value = false
  }
}

/**
 * 静默删掉一个空对话（没有消息，不弹确认框，删了不丢任何东西）。
 * 目的：点别的对话时把留下的空「新的对话」收走，侧边栏不会再攒出一排空壳。
 * 删不掉（已经被别处删了 / 网络抖动）就算了，不打断用户正在看的对话。
 */
async function discardEmptyConversation(id: number): Promise<void> {
  try {
    await deleteConversation(id)
    await loadConversations()
  } catch {
    // 空对话没删成功，最坏情况是侧边栏多一个空壳，下次切走还会再试
  }
}

async function newConversation(): Promise<void> {
  // 当前对话还没有消息时不建新的：重复点「新建对话」只会攒出一排空的「新的对话」。
  // 直接发消息即可，第一条消息就落在这个空对话里。
  if (!canCreateConversation.value) {
    return
  }
  // 列表里已经有一个空对话（比如上次刷新留下的）就直接跳过去，不建第二个。
  // 判据是标题还等于默认值：一旦发过首条消息，后端就把标题改成消息内容了。
  const existingEmpty = conversations.value.find((c) => c.title === DEFAULT_TITLE)
  if (existingEmpty) {
    await selectConversation(existingEmpty.id)
    return
  }
  try {
    const created = await createConversation()
    await loadConversations()
    activeId.value = null // 强制 selectConversation 重新拉取
    await selectConversation(created.id)
  } catch (e) {
    fatalError.value = errMsg(e)
  }
}

async function removeConversation(id: number): Promise<void> {
  if (!window.confirm('删除这个对话？全部消息将被删除，且无法恢复。')) {
    return
  }
  try {
    await deleteConversation(id)
    if (activeId.value === id) {
      stopStreaming()
      activeId.value = null
      messages.value = []
      activeIsEmpty.value = false
      resetPaging()
    }
    await loadConversations()
    if (activeId.value === null) {
      const first = conversations.value[0]
      if (first) {
        await selectConversation(first.id)
      }
    }
  } catch (e) {
    fatalError.value = errMsg(e)
  }
}

/**
 * 重命名会话：成功后只覆盖本地那一条，不重拉列表（重拉会把正在看的会话滚动位置之类的状态搅动）。
 * 失败走全局横幅：改名失败不该打断用户正在看的对话。
 */
async function onRenameConversation(id: number, title: string): Promise<void> {
  const target = conversations.value.find((c) => c.id === id)
  if (!target || target.title === title) {
    return
  }
  try {
    const updated = await renameConversation(id, title)
    target.title = updated.title
    fatalError.value = ''
  } catch (e) {
    fatalError.value = errMsg(e)
  }
}

/** 停止生成：中断 fetch，后端会保留已生成的部分并入库。 */
function stopStreaming(): void {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
}

async function send(): Promise<void> {
  const text = input.value.trim()
  if (!text || streaming.value) {
    return
  }
  fatalError.value = ''

  // 没有活动会话（比如刚删完）就先建一个
  let conversationId = activeId.value
  if (conversationId === null) {
    try {
      const created = await createConversation()
      await loadConversations()
      conversationId = created.id
      activeId.value = created.id
    } catch (e) {
      fatalError.value = errMsg(e)
      return
    }
  }

  input.value = ''
  activeIsEmpty.value = false // 这条消息一发出去，它就不是空对话了，切走时不该被删
  messages.value.push({ id: null, role: 'user', content: text })
  messages.value.push({ id: null, role: 'assistant', content: '', reasoning: '', streaming: true })
  // 从数组里取回响应式代理再改：直接改 push 进去的原始对象不会触发视图更新
  const reply = messages.value[messages.value.length - 1]!
  scrollToBottom()

  streaming.value = true
  abortController = new AbortController()
  try {
    await streamChat(
      conversationId,
      text,
      streamOptions(),
      streamHandlers(reply),
      abortController.signal,
    )
  } catch (e) {
    if (e instanceof Error && e.name === 'AbortError') {
      // 用户点了停止/切换会话：已生成部分后端已入库，这里静默收尾
    } else {
      reply.error = errMsg(e)
    }
  } finally {
    reply.streaming = false
    streaming.value = false
    abortController = null
    scrollToBottom()
    // 首条消息会触发后端自动起标题，刷新列表拿到新标题和新排序
    void loadConversations()
  }
}

/** 流式回调：思考追加到 reasoning、回答追加到 content、done 回填 id、error 写错误文案。send 与 regenerate 共用。 */
function streamHandlers(reply: UiMessage): StreamHandlers {
  return {
    onReasoning: (t) => {
      reply.reasoning = (reply.reasoning ?? '') + t
      scrollToBottom()
    },
    onDelta: (t) => {
      reply.content += t
      scrollToBottom()
    },
    onDone: (messageId, usage) => {
      if (messageId !== undefined) {
        reply.id = messageId
      }
      // 用量行当场回填：不这么做的话要等刷新页面才能看到这条回答烧了多少
      if (usage) {
        reply.model = usage.model
        reply.promptTokens = usage.prompt_tokens
        reply.completionTokens = usage.completion_tokens
        reply.reasoningTokens = usage.reasoning_tokens
      }
    },
    onError: (message) => {
      reply.error = message
    },
  }
}

/**
 * 重新生成：本地先摘掉最后一条助手消息，再以流式占位接上新回答。
 * 后端会删掉库里的旧回答、用同一条用户消息重跑，所以历史不会攒出两份回答。
 */
async function regenerate(): Promise<void> {
  const id = activeId.value
  if (id === null || streaming.value) {
    return
  }
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'assistant') {
    return
  }
  messages.value.pop()
  messages.value.push({ id: null, role: 'assistant', content: '', reasoning: '', streaming: true })
  // 从数组里取回响应式代理再改，理由同 send()
  const reply = messages.value[messages.value.length - 1]!
  scrollToBottom()

  streaming.value = true
  abortController = new AbortController()
  try {
    await streamRegenerate(id, streamOptions(), streamHandlers(reply), abortController.signal)
  } catch (e) {
    if (e instanceof Error && e.name === 'AbortError') {
      // 与 send() 同语义：中断时后端已保留已生成部分
    } else {
      reply.error = errMsg(e)
    }
  } finally {
    reply.streaming = false
    streaming.value = false
    abortController = null
    scrollToBottom()
    void loadConversations()
  }
}

/** Enter 发送、Shift+Enter 换行；中文输入法候选态的 Enter 不触发发送。 */
function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void send()
  }
}

onMounted(async () => {
  void loadLlmOptions()
  await loadConversations()
  const first = conversations.value[0]
  if (first) {
    await selectConversation(first.id)
  } else {
    await newConversation()
  }
})

/**
 * 组件卸载（退出登录、或 token 失效被弹回登录页）时必须掐断还在跑的 SSE：
 * 否则 fetch 会继续往一个已经不存在的界面上写增量，还白烧模型的 token。
 */
onBeforeUnmount(stopStreaming)
</script>

<template>
  <div class="app">
    <ConversationSidebar
      :conversations="conversations"
      :active-id="activeId"
      :can-create="canCreateConversation"
      @select="selectConversation"
      @create="newConversation"
      @remove="removeConversation"
      @rename="onRenameConversation"
    />

    <main class="chat">
      <header class="topbar">
        <span class="topbar-title">Chatbot</span>
        <div class="topbar-right">
          <button v-if="isAdmin" class="link-btn" type="button" @click="showUserDialog = true">用户管理</button>
          <button class="link-btn" type="button" @click="showPasswordDialog = true">修改密码</button>
          <span class="user-chip">
            <span class="user-name">{{ currentUser?.username }}</span>
            <span class="user-role" :class="{ admin: isAdmin }">{{ currentUser?.roleLabel }}</span>
          </span>
          <button class="link-btn danger" type="button" @click="logout">退出登录</button>
        </div>
      </header>

      <div v-if="fatalError" class="fatal-error">{{ fatalError }}</div>

      <div ref="scroller" class="messages">
        <div v-if="loadingMessages" class="hint">消息加载中…</div>
        <div v-else-if="messages.length === 0" class="hint">发送第一条消息，开始对话</div>
        <template v-else>
          <div v-if="hasMoreOlder" class="older">
            <button
              class="link-btn"
              type="button"
              :disabled="loadingOlder || streaming"
              :title="loadingOlder ? '正在加载' : '再往前翻 50 条'"
              @click="loadOlder"
            >
              {{ loadingOlder ? '加载更早的消息…' : '加载更早的消息' }}
            </button>
          </div>
          <MessageBubble
            v-for="(m, i) in messages"
            :key="m.id ?? 'pending-' + i"
            :message="m"
            :can-regenerate="m.role === 'assistant' && i === messages.length - 1 && !streaming"
            @regenerate="regenerate"
          />
        </template>
      </div>

      <footer class="input-bar">
        <label class="thinking-toggle" title="只对本条消息生效：开启后推理模型先思考再回答，首字更慢">
          <input v-model="thinkingOn" type="checkbox" :disabled="streaming" />
          思考模式
        </label>
        <select
          v-model="selectedModel"
          class="bar-select"
          :disabled="streaming || llmModels.length === 0"
          title="本轮使用的模型"
        >
          <option v-for="m in llmModels" :key="m" :value="m">{{ m }}</option>
        </select>
        <select
          v-model="thinkingBudgetSel"
          class="bar-select"
          :disabled="streaming || !thinkingOn"
          title="思考预算：思维链 token 上限。思考模式关闭时不可选"
        >
          <option v-for="c in THINKING_BUDGET_CHOICES" :key="c.value" :value="c.value">{{ c.label }}</option>
        </select>
        <div class="input-row">
          <textarea
            v-model="input"
            class="input"
            rows="1"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown="onKeydown"
          ></textarea>
          <button v-if="streaming" class="btn stop" type="button" @click="stopStreaming">停止</button>
          <button v-else class="btn send" type="button" :disabled="!input.trim()" @click="send">发送</button>
        </div>
      </footer>
    </main>

    <ChangePasswordDialog
      v-if="showPasswordDialog"
      @close="showPasswordDialog = false"
      @changed="onPasswordChanged"
    />
    <UserListDialog v-if="showUserDialog" @close="showUserDialog = false" />
  </div>
</template>

<style scoped>
.app {
  display: flex;
  height: 100%;
}

.chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
}

.topbar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
}

.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}

.link-btn {
  padding: 4px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--text);
  background: var(--bg);
}

.link-btn.danger:hover {
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 12%, transparent);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 13px;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  background: var(--bg);
  color: var(--text-muted);
}

.user-role.admin {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 14%, transparent);
}

.fatal-error {
  padding: 8px 16px;
  font-size: 14px;
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 10%, transparent);
  border-bottom: 1px solid var(--border);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.older {
  align-self: center;
}

/* .messages 是 flex column，不写 align-self 按钮会被拉成一整行宽 */
.older .link-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.hint {
  margin: auto;
  color: var(--text-muted);
  text-align: center;
}

.input-bar {
  padding: 10px 24px 16px;
  background: var(--panel);
  border-top: 1px solid var(--border);
}

.bar-select {
  padding: 4px 6px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--panel);
  color: var(--text);
  font-size: 12px;
}

.bar-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.thinking-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
  width: fit-content;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.input {
  flex: 1;
  resize: none;
  padding: 10px 12px;
  max-height: 160px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  line-height: 1.5;
}

.input:focus {
  outline: none;
  border-color: var(--accent);
}

.btn {
  padding: 10px 18px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  color: #fff;
}

.btn.send {
  background: var(--accent);
}

.btn.send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn.stop {
  background: var(--danger);
}
</style>