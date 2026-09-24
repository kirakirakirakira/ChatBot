<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/lib/markdown'

const props = defineProps<{
  content: string
  /** 流式生成中：在渲染结果末尾挂一个闪烁光标。 */
  streaming?: boolean
}>()

const html = computed(() => renderMarkdown(props.content))

/**
 * 复制按钮是 markdown 渲染产物，不在 Vue 事件体系里：
 * 在容器上做委托监听，靠 closest 找到按钮和它所属的代码块，不用给每个按钮单独绑事件。
 */
function onClick(e: MouseEvent): void {
  const target = e.target
  if (!(target instanceof HTMLElement)) {
    return
  }
  const button = target.closest('.copy-btn')
  if (!(button instanceof HTMLButtonElement)) {
    return
  }
  const code = button.closest('.code-block')?.querySelector('code')?.textContent ?? ''
  void copy(button, code)
}

async function copy(button: HTMLButtonElement, code: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(code)
    flash(button, '已复制')
  } catch {
    // 浏览器拒绝写剪贴板（权限 / 非安全上下文）：给明确反馈，别让用户以为成功了
    flash(button, '复制失败')
  }
}

function flash(button: HTMLButtonElement, text: string): void {
  const original = button.textContent ?? ''
  button.textContent = text
  button.disabled = true
  window.setTimeout(() => {
    button.textContent = original
    button.disabled = false
  }, 1200)
}
</script>

<template>
  <div
    class="markdown"
    :class="{ 'is-streaming': streaming }"
    @click="onClick"
    v-html="html"
  ></div>
</template>

<style scoped>
.markdown {
  line-height: 1.65;
  word-break: break-word;
}

.markdown :deep(> :first-child) {
  margin-top: 0;
}

.markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.markdown :deep(p) {
  margin: 0 0 8px;
}

.markdown :deep(h1),
.markdown :deep(h2),
.markdown :deep(h3),
.markdown :deep(h4) {
  margin: 12px 0 6px;
  line-height: 1.3;
}

.markdown :deep(h1) { font-size: 1.25em; }
.markdown :deep(h2) { font-size: 1.15em; }
.markdown :deep(h3),
.markdown :deep(h4) { font-size: 1.05em; }

.markdown :deep(ul),
.markdown :deep(ol) {
  margin: 0 0 8px;
  padding-left: 1.4em;
}

.markdown :deep(li) {
  margin: 2px 0;
}

/* 列表项里的段落不该再带段距，否则一个列表松得像三段话 */
.markdown :deep(li > p) {
  margin: 0;
}

.markdown :deep(blockquote) {
  margin: 8px 0;
  padding: 2px 10px;
  border-left: 3px solid var(--border);
  color: var(--text-muted);
}

.markdown :deep(a) {
  color: var(--accent);
}

/* 表格可能比气泡宽：包一层横向滚动，别把整个消息区撑变形 */
.markdown :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  margin: 8px 0;
  border-collapse: collapse;
}

.markdown :deep(th),
.markdown :deep(td) {
  border: 1px solid var(--border);
  padding: 4px 8px;
  font-size: 13px;
}

.markdown :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(127, 127, 127, 0.18);
  font-size: 0.9em;
}

/* 代码块刻意固定深色底，不跟暗色模式切换：浅色主题下深色代码块对比度更好，也省一套主题 CSS */
.markdown :deep(.code-block) {
  margin: 8px 0;
  border-radius: 8px;
  overflow: hidden;
  background: #0d1117;
}

.markdown :deep(.code-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px;
  background: #161b22;
}

.markdown :deep(.code-lang) {
  color: #8b949e;
  font-size: 12px;
}

.markdown :deep(.copy-btn) {
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #8b949e;
  font-size: 12px;
  cursor: pointer;
}

.markdown :deep(.copy-btn:hover) {
  color: #e6edf3;
  background: #21262d;
}

.markdown :deep(.copy-btn:disabled) {
  cursor: default;
}

.markdown :deep(pre) {
  margin: 0;
  padding: 10px 12px;
  overflow-x: auto;
}

/* 围栏代码的 <code> 只有 language-xx 类、没有 hljs 类（markdown-it 不加），
   所以上面的行内样式会命中它：inline 元素带背景 + padding 会在每个换行处画出一个个灰块。
   在这里整体覆盖回去。 */
.markdown :deep(pre code) {
  padding: 0;
  border-radius: 0;
  background: transparent;
  font-size: 12.5px;
  line-height: 1.55;
}

/* 流式光标挂在最后一个块的末尾（行内），比单独占一行更像「还在打字」 */
.markdown.is-streaming :deep(> :last-child)::after {
  content: '▍';
  animation: md-blink 1s step-start infinite;
}

@keyframes md-blink {
  50% {
    opacity: 0;
  }
}
</style>
