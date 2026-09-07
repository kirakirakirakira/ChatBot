<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import ConversationSidebar from '@/components/ConversationSidebar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import type { UiMessage } from '@/types'
import {
  createConversation,
  deleteConversation,
  getMessages,
  listConversations,
  streamChat,
} from '@/api'

const conversations = ref<Awaited<ReturnType<typeof listConversations>>>([])
const activeId = ref<number | null>(null)
const messages = ref<UiMessage[]>([])
const input = ref('')
const streaming = ref(false)
const loadingMessages = ref(false)
/** 思考开关：显式传 true/false（每条消息生效）。想改用服务端默认配置，把它设为 undefined 传给 api 即可。 */
const thinkingOn = ref(true)
/** 会话列表加载失败、删除失败这类全局错误，横幅展示。 */
const fatalError = ref('')

/** 新建对话的前提：没有选中对话，或选中的对话已经有消息。防止连点攒出一排空对话。 */
const canCreateConversation = computed(() => activeId.value === null || messages.value.length > 0)

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
  activeId.value = id
  loadingMessages.value = true
  try {
    const history = await getMessages(id)
    messages.value = history.map((m) => ({ id: m.id, role: m.role, content: m.content }))
    fatalError.value = ''
    scrollToBottom()
  } catch (e) {
    messages.value = []
    fatalError.value = errMsg(e)
  } finally {
    loadingMessages.value = false
  }
}

async function newConversation(): Promise<void> {
  // 当前对话还没有消息时不建新的：重复点「新建对话」只会攒出一排空的「新的对话」。
  // 直接发消息即可，第一条消息就落在这个空对话里。
  if (!canCreateConversation.value) {
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
      thinkingOn.value,
      {
        onReasoning: (t) => {
          reply.reasoning = (reply.reasoning ?? '') + t
          scrollToBottom()
        },
        onDelta: (t) => {
          reply.content += t
          scrollToBottom()
        },
        onDone: (messageId) => {
          if (messageId !== undefined) {
            reply.id = messageId
          }
        },
        onError: (message) => {
          reply.error = message
        },
      },
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

/** Enter 发送、Shift+Enter 换行；中文输入法候选态的 Enter 不触发发送。 */
function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void send()
  }
}

onMounted(async () => {
  await loadConversations()
  const first = conversations.value[0]
  if (first) {
    await selectConversation(first.id)
  } else {
    await newConversation()
  }
})
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
    />

    <main class="chat">
      <div v-if="fatalError" class="fatal-error">{{ fatalError }}</div>

      <div ref="scroller" class="messages">
        <div v-if="loadingMessages" class="hint">消息加载中…</div>
        <div v-else-if="messages.length === 0" class="hint">发送第一条消息，开始对话</div>
        <MessageBubble v-for="(m, i) in messages" :key="m.id ?? 'pending-' + i" :message="m" />
      </div>

      <footer class="input-bar">
        <label class="thinking-toggle" title="只对本条消息生效：开启后推理模型先思考再回答，首字更慢">
          <input v-model="thinkingOn" type="checkbox" :disabled="streaming" />
          思考模式
        </label>
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