import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/github-dark.css'

/**
 * Markdown 渲染器：模型回答里的代码块 / 列表 / 表格 / 标题不再是一坨纯文本。
 *
 * 安全两道锁：
 * 1. `html: false` 让 markdown-it 把输入里的原始 HTML 全部转义——模型输出里真塞 <script> 也只会显示成文本；
 * 2. 渲染结果再过一遍 DOMPurify。markdown-it 默认的 validateLink 已经拦掉 javascript: 这类协议，
 *    但「渲染不可信文本」这件事值得两道锁，任何一道被绕过都还有另一道。
 *
 * 高亮只注册 highlight.js 的 common 子集（约 40 种语言）：全量包体积大一倍多，
 * 而聊天里绝大多数代码是 js/ts/python/java/sql/json/shell，common 全覆盖；
 * 认不出的语言不高亮、原样输出，不报错。
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  // 单个换行也换行：模型和用户都习惯「回车就是换行」，严格 CommonMark 会把它当空格吃掉
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
      } catch {
        // 高亮失败就退回纯文本：渲染不该因为一段怪代码崩掉
      }
    }
    return ''
  },
})

/**
 * 给代码块包一层 .code-block，加语言标签和复制按钮。
 * 按钮是渲染产物的一部分、不在 Vue 事件体系里，点击靠 MarkdownContent 上的委托监听处理。
 */
const defaultFence = md.renderer.rules.fence
md.renderer.rules.fence = (tokens, idx, options, env, self) => {
  const lang = (tokens[idx]?.info ?? '').trim().split(/\s+/)[0] ?? ''
  const inner = defaultFence ? defaultFence(tokens, idx, options, env, self) : ''
  return (
    '<div class="code-block">' +
    '<div class="code-head">' +
    '<span class="code-lang">' + md.utils.escapeHtml(lang || 'text') + '</span>' +
    '<button class="copy-btn" type="button">复制</button>' +
    '</div>' +
    inner +
    '</div>'
  )
}

/** 渲染 + 消毒。流式期间每个 delta 都会调一次：markdown-it 渲染几 KB 文本是亚毫秒级，不值得做增量缓存。 */
export function renderMarkdown(source: string): string {
  return DOMPurify.sanitize(md.render(source))
}
