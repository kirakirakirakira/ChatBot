# chatbot-web 前端

Chatbot 的 Vue 3 前端：登录闸门 + 会话侧边栏 + SSE 流式对话。
**没有 vue-router**——页面切换就是换根组件。

> 启动步骤见仓库根目录 [README.md](../README.md)，架构全貌见 [PROJECT_OVERVIEW.md](../PROJECT_OVERVIEW.md)。
> 本文只写前端自身的实现约定。

## 环境要求

Node `^22.18.0 || >=24.12.0`（`package.json` 的 `engines`，由 Vite 8 + TypeScript 6 决定），低版本会直接 EBADENGINE。

```sh
npm install
npm run dev          # http://localhost:5173
npm run build        # = run-p type-check build-only，产物在 dist/
npm run preview      # 本地预览 dist/
npm run type-check   # vue-tsc --build，单独跑类型检查
```

`.vue` 的类型信息由 `vue-tsc` 提供（`tsc` 认不出来），编辑器侧需要装 Vue (Official) 扩展。

## 后端地址

前端代码里**不写死后端地址**。`vite.config.ts` 把 `/api` 代理到 `http://localhost:8089`，
REST 和 SSE 都走这一条代理。后端换端口只改 `vite.config.ts`，不要动业务代码。

同文件里还配了路径别名 `@` → `./src`，以及 `vite-plugin-vue-devtools`。

## 登录态

| 文件 | 职责 |
|---|---|
| `src/auth.ts` | `token` / `currentUser` 是模块级 `ref`，持久化到 `localStorage`；`isAuthenticated` / `isAdmin` 是 computed；`clearSession()` 清空本地登录态 |
| `src/api.ts` | 统一挂 `Authorization: Bearer`，统一处理 401，SSE 读流 |
| `src/App.vue` | **权限闸门**：启动时调 `GET /api/auth/me` 验证 token，成功前渲染 `LoginView`，成功后渲染 `ChatView` |

任何接口返回 **401 都当作「会话已失效」**：`api.ts` 调 `clearSession()`，`App.vue` 随之弹回登录页。
因此不存在绕过登录能访问的页面，也不需要路由守卫。

> **侧边栏就是「我的会话」**：后端按 `conversation.owner_id` 隔离，换账号登录看到的是另一个人的列表。
> 越权访问别人的会话后端返回 404，前端当成「会话不存在」处理即可，不需要额外分支。

输入条上有两个选择器：**模型**（清单来自 `GET /api/llm/options`，即后端 `llm.available-models`）和**思考强度**（`thinking_budget`：不限 / 4096 / 16384 / 131072）。
两者都随每条消息下发并存在 localStorage；存过的模型若已不在白名单（配置改了），回落到服务端默认，而不是留一个后端会 400 的 id。
助手气泡底部有一行用量：`模型 · 输入 x / 输出 y tokens（含思考 z）`。

消息历史是**游标分页**：`getMessages(id, {before, limit})` 只取最新一页（默认 50 条），
`ChatView` 在还有更早消息时于消息区顶部显示「加载更早的消息」，点击后把上一页插到列表头部并补偿滚动位置
（不补偿的话，往顶部插 50 条会把视口顶下去，用户正在读的那条消息直接跑掉）。
`types.ts` 的 `MessagePage` 对应后端 `MessagePageVO`。

改密码成功后后端会换发新 token（响应体就是一份新的 `LoginResponse`），前端用它覆盖本地 token，当前会话不中断。
改**系统提示词**则不换发 token（它不是安全事件）：响应是一份 `UserVO`，前端用它覆盖本地 `currentUser` 即可。

## SSE 流式解析

后端的 chat 接口是 **POST + SSE**，浏览器原生 `EventSource` 只支持 GET，**用不了**。
必须用 `fetch` + `ReadableStream` 自己解析，三个要点：

1. 按**空行**切帧，每帧去掉 `data: ` 前缀后再 `JSON.parse`
2. `TextDecoder` 必须开 `stream: true`——否则一个多字节中文字符被 chunk 切成两半时会解出乱码
3. token 放请求头，**不要放 URL 查询参数**（会进各种访问日志）

事件契约（reasoning / delta / done / error 的形状与语义）由后端定义，见 [chatbot/README.md](../chatbot/README.md)。
前端侧的约定：

- `reasoning` 是思考过程，不属于消息正文。流式期间收到第一帧就显示「思考中…」并做成可折叠；生成结束后后端已把它存进 `message.reasoning`，所以**刷新页面后历史消息的思考也能展开重读**（`UiMessage.reasoning` 从接口读回）
- 收到第一帧 `delta` 再切到正文渲染
- 遇到不认识的 `type` 直接忽略，保持向后兼容
- **停止生成 = abort 这个 fetch**，不需要调额外接口；后端会停止调用模型并把已生成的部分入库，刷新页面能看到
- `done` 事件除 `messageId` 外还带 `model` 与用量（`prompt_tokens` / `completion_tokens` / `reasoning_tokens`）：前端在 `onDone` 里当场回填，用量行不用等刷新页面才出现
- **重新生成 = `POST /{id}/regenerate`**，SSE 契约与 `/chat` 相同（`api.ts` 里两者共用 `consumeSse()`）。前端先本地摘掉最后一条助手消息再接流；后端会删库里的旧回答、用同一条用户消息重跑，所以历史不会攒出两份回答
- 空闲超过 60 秒连接会被中间层掐断，正常情况下后端转发思考帧会一直有字节流动

## Markdown 渲染

助手消息正文走 `lib/markdown.ts` + `components/MarkdownContent.vue`：标题 / 列表 / 表格 / 引用 / 代码块都正常渲染，
代码块带语言标签、语法高亮（highlight.js 的 common 语言子集）和一键复制按钮。

三个刻意的取舍：

- **用户消息不渲染 Markdown**，保持纯文本。用户输入的是「话」不是文档，把里面的 `* _ #` 渲染掉只会让人困惑。
- **安全两道锁**：markdown-it 开 `html:false`（输入里的原始 HTML 全部转义成文本），渲染结果再过一遍 DOMPurify。
  渲染的是模型输出，属于不可信文本，值得两道锁。
- **代码块固定深色底**，不跟暗色模式切换：浅色主题下深色代码块对比度更好，也省一套主题 CSS。

流式生成期间每个 delta 都会重渲染一次：markdown-it 渲染几 KB 文本是亚毫秒级，不做增量缓存。
复制按钮是渲染产物、不在 Vue 事件体系里，靠容器上的委托监听处理；剪贴板写失败会显示「复制失败」，不假装成功。

## 视觉设计

设计语言参考 ChatGPT / Claude 的现行界面：**极简中性、大留白、弱边框**。几个刻意的决定：

- 配色是暖中性纸感（浅 `#f6f5f2` / 暗 `#161512`），跟随系统 `prefers-color-scheme`，不做手动主题切换；accent 保留绿色系作为品牌延续
- 消息列居中限宽 760px：超宽屏上整行铺满的文字读起来非常累
- **助手消息不套气泡**、带头像；用户消息是浅色圆角 pill——两种角色的视觉重量刻意不同
- 顶栏几乎隐形：只有会话标题和头像菜单，管理入口全收进下拉
- 代码块固定深色底，不跟暗色模式切换（浅色主题下深色代码块对比度更好，也省一套主题 CSS）
- 输入条的开关与选择：**思考**、**联网**（默认关——搜索按次计费）、模型、思考强度，都随每条消息下发；思考与联网的选择存 localStorage

## 目录结构

    src/
      api.ts        请求封装：鉴权头、401 处理、SSE 读流、消息分页参数
      auth.ts       登录态：token / currentUser / isAdmin
      types.ts      类型定义，与后端 DTO / VO 一一对应
      lib/markdown.ts  markdown-it + highlight.js + DOMPurify：助手消息的 Markdown 渲染
      App.vue       根组件 = 权限闸门
      main.ts       入口
      views/
        LoginView.vue   登录页
        ChatView.vue    聊天主界面：会话列表 + 消息区 + 输入框 + 思考开关 / 模型 / 思考强度 + 停止生成
      components/
        ConversationSidebar.vue   会话列表，按 updated_at 倒序，支持新建 / 删除 / 双击改名
        MessageBubble.vue         消息气泡，区分 user / assistant；助手消息走 Markdown、用户消息纯文本 + 思考折叠 + 重新生成按钮
        MarkdownContent.vue       Markdown 渲染容器：代码高亮 + 代码块复制按钮 + 流式光标
        ChangePasswordDialog.vue  修改密码，所有人可见
        SystemPromptDialog.vue    系统提示词（人设），所有人可见；保存后下一条消息立即生效
        UserListDialog.vue        用户管理，仅 isAdmin 时可见
        UserMenu.vue              顶栏头像下拉：用户管理 / 系统提示词 / 修改密码 / 退出登录
      assets/main.css

新增页面：在 `views/` 下加组件，在 `App.vue` 里按条件切换渲染（没有 router）。