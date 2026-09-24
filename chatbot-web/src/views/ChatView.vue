<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ConversationSidebar from '@/components/ConversationSidebar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import SystemPromptDialog from '@/components/SystemPromptDialog.vue'
import UserListDialog from '@/components/UserListDialog.vue'
import UserMenu from '@/components/UserMenu.vue'
import type { CurrentUser, LoginResult, UiMessage } from '@/types'
import type { StreamHandlers, StreamOptions } from '@/api'
import { clearSession, currentUser, isAdmin, setSession, token } from '@/auth'
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
/** 顶栏标题 = 当前会话标题；没选中时给个占位，避免顶栏空一块。 */
const activeTitle = computed(() => {
  const c = conversations.value.find((x) => x.id === activeId.value)
  return c ? c.title : '新的对话'
})

/** 空状态的开场建议：点一下填进输入框，比让用户面对空白输入框发呆友好。 */
const SUGGESTIONS = [
  '解释一下 SSE 和 WebSocket 的区别',
  '帮我写一个 MySQL 分页查询',
  '用三句话讲清楚什么是向量数据库',
]

const composer = ref<HTMLTextAreaElement | null>(null)

function useSuggestion(text: string): void {
  input.value = text
  void nextTick(() => composer.value?.focus())
}

/** 输入框自动长高：固定 rows=1 会让长消息变成滚动条地狱；上限 200px 之后交还滚动。 */
function autoGrow(): void {
  const el = composer.value
  if (!el) {
    return
  }
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

watch(input, () => void nextTick(autoGrow))

/** 会话列表加载失败、删除失败这类全局错误，横幅展示。 */
const fatalError = ref('')
const showPasswordDialog = ref(false)
/** 系统提示词（人设）弹窗：所有用户可见，改的是自己那份。 */
const showPromptDialog = ref(false)
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

/** 保存人设后只更新本地用户信息：token 不变、不用重新登录，下一条消息就带上新人设。 */
function onPromptChanged(user: CurrentUser): void {
  // token 在这里必然非空（没登录根本看不到顶栏），但用 if 收窄比 ! 断言更诚实
  if (token.value) {
    setSession(token.value, user)
  }
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
        <span class="topbar-title" :title="activeTitle">{{ activeTitle }}</span>
        <UserMenu
          v-if="currentUser"
          :user="currentUser"
          :is-admin="isAdmin"
          @users="showUserDialog = true"
          @prompt="showPromptDialog = true"
          @password="showPasswordDialog = true"
          @logout="logout"
        />
      </header>

      <div v-if="fatalError" class="fatal-error">{{ fatalError }}</div>

      <div ref="scroller" class="messages">
        <div class="thread">
          <div v-if="loadingMessages" class="hint">消息加载中…</div>

          <div v-else-if="messages.length === 0" class="empty">
            <div class="empty-mark" aria-hidden="true">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3" /><path d="M18.4 5.6 16.3 7.7" /><path d="M21 12h-3" /><path d="M5.6 7.7 7.7 5.6" /><path d="M3 12h3" /><path d="M12 21a9 9 0 0 0 9-9H3a9 9 0 0 0 9 9Z" /></svg>
            </div>
            <h2 class="empty-title">今天想聊点什么？</h2>
            <p class="empty-sub">选一个开场，或直接输入你的问题</p>
            <div class="chips">
              <button v-for="s in SUGGESTIONS" :key="s" class="chip" type="button" @click="useSuggestion(s)">
                {{ s }}
              </button>
            </div>
          </div>

          <template v-else>
            <div v-if="hasMoreOlder" class="older">
              <button
                class="older-btn"
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
      </div>

      <footer class="composer-wrap">
        <div class="composer">
          <textarea
            ref="composer"
            v-model="input"
            class="composer-input"
            rows="1"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown="onKeydown"
          ></textarea>
          <div class="composer-bar">
            <div class="composer-left">
              <label
                class="pill-toggle"
                :class="{ on: thinkingOn }"
                title="只对本条消息生效：开启后推理模型先思考再回答，首字更慢"
              >
                <input v-model="thinkingOn" type="checkbox" :disabled="streaming" />
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v3" /><path d="M18.4 5.6 16.3 7.7" /><path d="M21 12h-3" /><path d="M5.6 7.7 7.7 5.6" /><path d="M3 12h3" /><path d="M12 21a9 9 0 0 0 9-9H3a9 9 0 0 0 9 9Z" /></svg>
                思考
              </label>
              <select
                v-model="selectedModel"
                class="pill-select"
                :disabled="streaming || llmModels.length === 0"
                title="本轮使用的模型"
              >
                <option v-for="m in llmModels" :key="m" :value="m">{{ m }}</option>
              </select>
              <select
                v-model="thinkingBudgetSel"
                class="pill-select"
                :disabled="streaming || !thinkingOn"
                title="思考预算：思维链 token 上限。思考模式关闭时不可选"
              >
                <option v-for="c in THINKING_BUDGET_CHOICES" :key="c.value" :value="c.value">{{ c.label }}</option>
              </select>
            </div>
            <button
              v-if="streaming"
              class="round-btn stop"
              type="button"
              title="停止生成"
              @click="stopStreaming"
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2" /></svg>
            </button>
            <button
              v-else
              class="round-btn send"
              type="button"
              :disabled="!input.trim()"
              title="发送"
              @click="send"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5" /><path d="m5 12 7-7 7 7" /></svg>
            </button>
          </div>
        </div>
        <p class="composer-note">模型回答仅供参考，重要信息请自行核实</p>
      </footer>
    </main>

    <ChangePasswordDialog
      v-if="showPasswordDialog"
      @close="showPasswordDialog = false"
      @changed="onPasswordChanged"
    />
    <UserListDialog v-if="showUserDialog" @close="showUserDialog = false" />
    <SystemPromptDialog
      v-if="showPromptDialog"
      :initial="currentUser?.systemPrompt ?? ''"
      @close="showPromptDialog = false"
      @changed="onPromptChanged"
    />
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

/* 顶栏刻意做得几乎隐形：只有会话标题和头像菜单，聊天区才是主角 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 20px;
}

.topbar-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
}

.fatal-error {
  margin: 0 20px 8px;
  padding: 8px 14px;
  border-radius: var(--radius-m);
  font-size: 13.5px;
  color: var(--danger);
  background: color-mix(in srgb, var(--danger) 9%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger) 22%, transparent);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 24px 24px;
}

/* 内容列居中限宽：超宽屏上整行铺满的文字读起来非常累 */
.thread {
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.older {
  align-self: center;
}

.older-btn {
  padding: 5px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--panel);
  color: var(--text-muted);
  font-size: 12.5px;
  cursor: pointer;
  transition: color 120ms ease, border-color 120ms ease;
}

.older-btn:hover:not(:disabled) {
  color: var(--text);
  border-color: var(--border-strong);
}

.older-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.hint {
  margin: auto;
  padding: 40px 0;
  color: var(--text-muted);
  text-align: center;
}

/* ---------- 空状态 ---------- */
.empty {
  margin: auto;
  padding: 48px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.empty-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  margin-bottom: 8px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 45%, #2b6cb0));
  color: #fff;
  box-shadow: var(--shadow-1);
}

.empty-title {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0.3px;
}

.empty-sub {
  margin: 0 0 14px;
  font-size: 13.5px;
  color: var(--text-muted);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 560px;
}

.chip {
  padding: 7px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  background: var(--panel);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 120ms ease, background 120ms ease, color 120ms ease, transform 80ms ease;
}

.chip:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.chip:active {
  transform: scale(0.97);
}

/* ---------- 合成输入框 ---------- */
.composer-wrap {
  padding: 0 24px 14px;
}

.composer {
  max-width: 760px;
  margin: 0 auto;
  padding: 10px 12px 8px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 22px;
  box-shadow: var(--shadow-1);
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

.composer:focus-within {
  border-color: color-mix(in srgb, var(--accent) 55%, var(--border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 14%, transparent), var(--shadow-1);
}

.composer-input {
  display: block;
  width: 100%;
  padding: 4px 6px;
  border: none;
  background: transparent;
  resize: none;
  line-height: 1.55;
  max-height: 200px;
}

.composer-input:focus {
  outline: none;
}

.composer-input::placeholder {
  color: var(--text-muted);
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 4px;
}

.composer-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

/* 思考开关做成 pill：比裸 checkbox 更像「一个模式」而不是「一个表单项」 */
.pill-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
  color: var(--text-muted);
  font-size: 12.5px;
  cursor: pointer;
  user-select: none;
  transition: all 120ms ease;
}

.pill-toggle input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.pill-toggle.on {
  color: var(--accent-strong);
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
  background: var(--accent-soft);
}

.pill-select {
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--text-muted);
  font-size: 12.5px;
  cursor: pointer;
  max-width: 170px;
  transition: all 120ms ease;
}

.pill-select:hover:not(:disabled) {
  border-color: var(--border);
  background: var(--panel-2);
  color: var(--text);
}

.pill-select:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.round-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: background 120ms ease, transform 80ms ease, opacity 120ms ease;
}

.round-btn.send {
  background: var(--accent);
  color: #fff;
}

.round-btn.send:hover:not(:disabled) {
  background: var(--accent-strong);
}

.round-btn.send:active:not(:disabled) {
  transform: scale(0.94);
}

.round-btn.send:disabled {
  background: color-mix(in srgb, var(--text) 12%, transparent);
  color: var(--text-muted);
  cursor: not-allowed;
}

.round-btn.stop {
  background: color-mix(in srgb, var(--text) 10%, transparent);
  color: var(--text);
}

.round-btn.stop:hover {
  background: color-mix(in srgb, var(--danger) 16%, transparent);
  color: var(--danger);
}

.composer-note {
  max-width: 760px;
  margin: 8px auto 0;
  text-align: center;
  font-size: 11.5px;
  color: var(--text-muted);
  opacity: 0.75;
}
</style>
