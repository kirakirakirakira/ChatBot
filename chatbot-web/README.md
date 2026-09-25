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
| `src/api/client.ts` | **全站唯一的传输层**：统一挂 `Authorization: Bearer`、统一处理 401（`clearSession()`）；不 import router，避免和 `router → guards → api` 形成循环依赖 |
| `src/api.ts` | 聊天与登录的**接口清单** + SSE 读流。新增别的平级功能模块请另开 `src/api/<模块>.ts`，同样只用 `client.ts` 的 `request()` |
| `src/App.vue` | `RouterView` + 对 `isAuthenticated` 的全局 watch：登录态一旦消失（401 / 主动退出 / 被禁用）就把界面送回 `/login?next=` |
| `src/router/guards.ts` | **权限闸门**：`beforeEach` 先 `await ensureSession()`（本地有 token 才问后端、且只问一次），再按 `meta.public` / `meta.requiresAdmin` 放行或跳 `/login?next=` / `/403` |
| `src/session.ts` | `ensureSession()`：把原来 App.vue onMounted 里的「验证本地 token」搬进守卫，promise 按页面加载缓存 |
| `src/router/routes.ts` | **模块注册表**：加一个平级模块 = 在这里加一条带 `meta.moduleId` 的路由记录，导航与守卫自动生效 |

任何接口返回 **401 都当作「会话已失效」**：`api/client.ts` 调 `clearSession()`，App.vue 的 watch 随之把界面送回登录页（带上 `?next=`，登录后回原页）。
因此不存在绕过登录能访问的 URL：闸门在 `router/guards.ts` 里，直接在地址栏输地址也一样拦。

> **侧边栏就是「我的会话」**：后端按 `conversation.owner_id` 隔离，换账号登录看到的是另一个人的列表。
> 越权访问别人的会话后端返回 404，前端当成「会话不存在」处理即可，不需要额外分支。

输入条上有两个选择器：**模型**（清单来自 `GET /api/llm/options`，即后端 `llm.available-models`）和**思考强度**（`thinking_budget`：不限 / 4096 / 16384 / 131072）。
两者都随每条消息下发并存在 localStorage；存过的模型若已不在白名单（配置改了），回落到服务端默认，而不是留一个后端会 400 的 id。
助手气泡底部有一行用量：`模型 · 输入 x / 输出 y tokens（含思考 z）`。

**图片输入**：输入条的「图片」按钮（+ 粘贴截图 + 拖拽）只在选中模型支持图片时出现
（`GET /api/llm/options` 的 `visionModels`，即后端 `llm.vision-models`）。流程是「选中即上传」：
`uploadAttachment()` 先拿附件 id，缩略图条显示上传中 / 失败，点发送时把 id 放进 `attachmentIds`。
本地先做四道校验（张数 ≤4、MIME 白名单、≤5MB、全部上传成功），后端还有一道同样的兜底。
历史消息里的图由 `AttachmentThumb.vue` 按 id 调 `fetchAttachmentUrl()` 取字节转 objectURL——
**不能用 `<img src="/api/attachments/1">`**：img 标签带不了 `Authorization` 头，而后端刻意不做 `?token=` 兜底。
objectURL 的所有权规则：本地创建的归 `ChatView`（切会话 / 卸载时统一 revoke），组件自己 fetch 的归组件（卸载时 revoke）。

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
- 待发送图片缩略图条放在输入框上方：上传状态（上传中 / 失败）和移除按钮都在缩略图上，不占输入框空间；拖图进来时合成框高亮给落点反馈

## 目录结构

    src/
      api/client.ts 传输层：fetch 封装、鉴权头、401 兜底（全站唯一一份）
      api.ts        聊天与登录的接口清单、SSE 读流、消息分页参数
      api/userAdmin.ts  用户管理模块接口（/api/admin/users 八个）；新模块照这个开新文件
      auth.ts       登录态：token / currentUser / isAdmin
      types.ts      类型定义，与后端 DTO / VO 一一对应（含管理端 AdminUser / Page / UserAdminOptions）
      session.ts    ensureSession()：本地 token 的一次性有效性确认（守卫里 await）
      router/       index（createRouter）/ routes（模块注册表）/ guards（登录与角色闸门）/ paths（路径常量）
      layouts/      AppShell.vue：左侧模块导航 + 内容区外壳，平级模块都挂在它下面
      lib/markdown.ts  markdown-it + highlight.js + DOMPurify：助手消息的 Markdown 渲染
      App.vue       RouterView + 掉登录态回登录页的全局 watch
      main.ts       入口：createApp(App).use(router).mount('#app')
      views/
        LoginView.vue   登录页（顶层路由，不在外壳里）
        ChatView.vue    聊天主界面：会话列表 + 消息区 + 输入框 + 思考开关 / 模型 / 思考强度 + 停止生成
        admin/UsersView.vue  用户管理（/admin/users）：表格 + 筛选 + 分页 + 各操作弹窗
        ForbiddenView.vue  /403：已登录但权限不够
        NotFoundView.vue   其余一切路径的兜底
      components/
        ConversationSidebar.vue   会话列表，按 updated_at 倒序，支持新建 / 删除 / 双击改名
        MessageBubble.vue         消息气泡，区分 user / assistant；助手消息走 Markdown、用户消息纯文本 + 图片缩略图 + 思考折叠 + 重新生成按钮
        AttachmentThumb.vue       单张图的缩略图：有本地 url 直接用，只有 id 时 fetch 字节转 objectURL（自己 revoke）；点击新标签看原图
        MarkdownContent.vue       Markdown 渲染容器：代码高亮 + 代码块复制按钮 + 流式光标
        ChangePasswordDialog.vue  修改密码；force 模式下关不掉（管理员重置过密码时由外壳弹出）
        SystemPromptDialog.vue    系统提示词（人设），所有人可见；保存后下一条消息立即生效
        UserMenu.vue              顶栏头像下拉：用户管理（跳 /admin/users）/ 系统提示词 / 修改密码 / 退出登录
        ModuleNav.vue             左侧模块导航条（条目从 routes.ts 派生）+ 底部账号按钮 / 退出登录
        common/ConfirmDialog.vue  通用二次确认；requireText 非空时输入一致才点亮确认（删号用）
        admin/UserFormDialog.vue  新建用户（可一键生成随机密码）
        admin/ResetPasswordDialog.vue  重置密码（随机密码只展示这一次）
        icons/                    导航图标（24×24 描边、stroke=currentColor）
      assets/main.css

新增页面 / 平级功能模块：在 `views/` 下加组件，在 `router/routes.ts` 里加一条路由记录。
带上 `meta.moduleId` / `title` / `icon` / `order` 就会自动出现在左侧模块导航条上；
带上 `meta.requiresAdmin` 守卫就会自动拦成 `/403`。导航组件与守卫都不用改。

## 路由与部署

- history 模式（URL 里没有 `#`）：`/login`、`/chat`、`/403`，其余路径落 404 页。
- 开发期 Vite 自带回退；**生产部署必须让静态服务器把未命中路径回退到 `index.html`**
  （nginx：`try_files $uri /index.html`），否则直接访问或刷新 `/admin/users` 会拿到服务器 404。
- 登录页是顶层路由（不进外壳），其余页面都挂在 `layouts/AppShell.vue` 下：左侧 56px 模块导航 + 内容区。

## 平级功能模块（以 /admin/users 为例）

- 一个模块 = `views/<模块>/` + `src/api/<模块>.ts` + `routes.ts` 一条记录；管理端接口在后端挂 `/api/admin/**`。
- `meta.requiresAdmin` 的模块：守卫拦成 `/403`、导航条对普通用户隐藏；真正的权限在后端类级 `@RequireAdmin`，前端只是不给人看一个必然 403 的按钮。
- `UserVO.mustChangePassword=true` 时外壳弹**关不掉的改密框**（管理员重置过密码），改完才能用任何模块。
- 退出登录在导航条底部的账号按钮里：管理页没有聊天顶栏的头像菜单，退出是外壳级的事。
- 管理台视图懒加载：构建产物里是独立分包，普通用户的 bundle 不含管理台代码。