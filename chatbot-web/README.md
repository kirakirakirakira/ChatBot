# chatbot-web 前端

Chatbot 的 Vue 3 前端：登录闸门 + 会话侧边栏 + SSE 流式对话 + 平级功能模块（个人信息 / 用户管理 / 操作记录）。
路由用 **vue-router 4**（history 模式）：URL 即模块，权限闸门在 `router/guards.ts`，外壳在 `layouts/AppShell.vue`。

> 启动步骤见仓库根目录 [README.md](../README.md)，架构与组件清单见 [PROJECT_OVERVIEW.md](../PROJECT_OVERVIEW.md)。
> 本文只写前端自身的实现约定。

## 环境要求

Node `^22.18.0 || >=24.12.0`（`package.json` 的 `engines`，由 Vite 8 + TypeScript 6 决定），低版本直接 EBADENGINE。

```sh
npm install
npm run dev          # http://localhost:5173
npm run build        # = run-p type-check build-only，产物在 dist/
npm run preview      # 本地预览 dist/
npm run type-check   # vue-tsc --build，只跑类型检查
```

`.vue` 的类型信息由 `vue-tsc` 提供（`tsc` 认不出来），编辑器需要装 Vue (Official) 扩展。

## 后端地址

前端代码里**不写死后端地址**：`vite.config.ts` 把 `/api` 代理到 `http://localhost:8089`，REST 与 SSE 都走这一条代理。
默认值可用 `BACKEND_URL` 覆盖（`BACKEND_URL=http://localhost:8099 npm run dev`）。
**注意**：浏览器对同源 POST 也会带 `Origin`，换了端口要把新源加进后端 `CORS_ALLOWED_ORIGINS`，否则登录会拿到空响应体的 403（界面只显示「请求失败（HTTP 403）」，很容易误判成密码错）。

同文件还配了别名 `@` → `./src` 与 `vite-plugin-vue-devtools`。

## 登录态

| 文件 | 职责 |
|---|---|
| `src/auth.ts` | `token` / `currentUser` 是模块级 `ref`，持久化到 `localStorage`；`isAuthenticated` / `isAdmin` / `isSuperAdmin` 是 computed；`displayName`（昵称优先、回退登录名）；`clearSession()` 清本地登录态 |
| `src/api/client.ts` | **全站唯一传输层**：统一挂 `Authorization: Bearer`、统一处理 401；不 import router，避免循环依赖 |
| `src/api.ts` | 聊天、登录与「管自己」的接口清单（含 `updateMyProfile` / `fetchMyStats` / `revokeMySessions`）+ SSE 读流；新的平级模块另开 `src/api/<模块>.ts`，只用 `client.ts` 的 `request()` |
| `src/App.vue` | `RouterView` + 全局 watch：登录态一旦消失（401 / 主动退出 / 被禁用）就送回 `/login?next=` |
| `src/router/guards.ts` | **权限闸门**：`beforeEach` 先 `await ensureSession()`（本地有 token 才问后端、且只问一次），再按 `meta.public` / `meta.requiresAdmin` 放行或跳 `/login?next=` / `/403` |
| `src/session.ts` | `ensureSession()`：按页面加载缓存的一次性 token 有效性确认 |
| `src/router/routes.ts` | **模块注册表**：加一个平级模块 = 加一条带 `meta.moduleId` 的记录，导航与守卫自动生效 |

任何接口返回 **401 都当作「会话已失效」**：`client.ts` 调 `clearSession()`，App.vue 的 watch 把界面送回登录页（带 `?next=`，登录后回原页）。因此没有能绕过登录的 URL：闸门在 `guards.ts`，直接输地址也一样拦。

> 侧边栏就是「我的会话」：后端按 `conversation.owner_id` 隔离，换账号看到的是另一个人的列表；访问别人的会话后端返回 404，前端当成「会话不存在」处理即可。

输入条上有两个选择器：**模型**（清单来自 `GET /api/llm/options`）和**思考强度**（不限 / 4096 / 16384 / 131072），都随每条消息下发并存在 localStorage；存过的模型若已不在白名单就回落到服务端默认。助手气泡底部显示一行用量：`模型 · 输入 x / 输出 y tokens（含思考 z）`。

**图片输入**：输入条的「图片」按钮（+ 粘贴 + 拖拽）只在选中模型支持图片时出现（`GET /api/llm/options` 的 `visionModels`）。流程是「选中即上传」：`uploadAttachment()` 先拿附件 id，点发送时把 id 放进 `attachmentIds`。本地先做四道校验（张数 ≤4、MIME 白名单、≤5MB、全部上传成功），后端还有一道兜底。历史消息里的图由 `AttachmentThumb.vue` 按 id 调 `fetchAttachmentUrl()` 取字节转 objectURL——**不能用 `<img src="/api/attachments/1">`**，因为 img 带不了 `Authorization`，而后端刻意不做 `?token=` 兜底。

消息历史是**游标分页**：`getMessages(id, {before, limit})` 只取最新一页（默认 50 条），还有更早消息时顶部显示「加载更早的消息」，点击后把上一页插到列表头部并补偿滚动位置（不补偿会把视口顶下去）。

改密码成功后后端换发新 token（响应体是新的 `LoginResponse`），前端用它覆盖本地 token，当前会话不中断；改系统提示词不换 token（响应用户 `UserVO` 覆盖本地 `currentUser` 即可）。

## SSE 流式解析

后端的 chat 接口是 **POST + SSE**，浏览器原生 `EventSource` 只支持 GET，**用不了**，必须用 `fetch` + `ReadableStream` 自己解析。三个要点：

1. 按**空行**切帧，每帧去掉 `data: ` 前缀后再 `JSON.parse`
2. `TextDecoder` 必须开 `stream: true`——否则多字节中文字符被 chunk 切成两半时会解出乱码
3. token 放请求头，**不要放 URL 查询参数**（会进各种访问日志）

事件契约（reasoning / delta / done / error）由后端定义，见 [chatbot/README.md](../chatbot/README.md)。前端侧约定：

- `reasoning` 是思考过程，不属于正文；收到第一帧就显示「思考中…」并做成可折叠，刷新后历史消息的思考也能展开重读（`UiMessage.reasoning` 从接口读回）
- 收到第一帧 `delta` 再切到正文渲染
- 不认识的事件 `type` 直接忽略，保持向后兼容
- **停止生成 = abort 这个 fetch**，不需要额外接口；后端会停止模型并把已生成部分入库
- `done` 事件带 `messageId` / `model` / 用量，前端在 `onDone` 里当场回填，不用等刷新
- **重新生成 = `POST /{id}/regenerate`**，SSE 契约与 `/chat` 相同（共用 `consumeSse()`）；前端先本地摘掉最后一条助手消息再接流，后端会删旧回答重跑，历史不会攒出两份
- 空闲超过 60 秒连接会被中间层掐断，正常情况下后端转发的思考帧会一直有字节流动

## Markdown 渲染

助手消息正文走 `lib/markdown.ts` + `components/MarkdownContent.vue`：标题 / 列表 / 表格 / 引用 / 代码块正常渲染，代码块带语言标签、语法高亮（highlight.js common 子集）和一键复制。三个刻意的取舍：

- **用户消息不渲染 Markdown**，保持纯文本——用户输入的是「话」不是文档，把里面的 `* _ #` 渲染掉只会让人困惑。
- **安全两道锁**：markdown-it 开 `html:false`（原始 HTML 转义），渲染结果再过一遍 DOMPurify。渲染的是模型输出，属于不可信文本。
- **代码块固定深色底**，不跟暗色模式切换。注意 highlight.js 的配色挂在 `.hljs` 类上，而 markdown-it 只加 `language-xx`，所以 fence 渲染器必须自己给 `<code>` 补 `hljs`；漏了主题色不生效，浅色模式下就是「黑底黑字」。

流式生成期间每个 delta 都会重渲染一次：markdown-it 渲染几 KB 是亚毫秒级，不做增量缓存。复制按钮是渲染产物、不在 Vue 事件体系里，靠容器委托监听，剪贴板写失败会显示「复制失败」。

## 视觉设计

参考 ChatGPT / Claude 的现行界面：极简中性、大留白、弱边框。几个刻意的决定：

- 配色是暖中性纸感（浅 `#f6f5f2` / 暗 `#161512`），跟随系统 `prefers-color-scheme`，不做手动主题切换；accent 保留绿色系
- 消息列居中限宽 760px——超宽屏整行铺满很累
- **助手消息不套气泡**、带头像；用户消息是浅色圆角 pill，两种角色视觉重量刻意不同
- 顶栏几乎隐形：只有会话标题和头像菜单，管理入口收进下拉
- 输入条开关与选择（思考 / 联网 / 模型 / 思考强度）随每条消息下发；思考与联网的选择存 localStorage，联网默认关（按次计费）
- 待发送图片缩略图条在输入框上方：上传状态与移除按钮都在缩略图上，不占输入框空间

## 目录结构

    src/
      api/client.ts 传输层：fetch 封装、鉴权头、401 兜底（全站唯一一份）
      api.ts        聊天与登录接口、SSE 读流、消息分页参数
      api/userAdmin.ts  用户管理模块接口（/api/admin/users + /api/admin/audit）；新模块照这个开新文件
      auth.ts       登录态：token / currentUser / isAdmin / displayName
      types.ts      类型定义，与后端 DTO / VO 一一对应
      session.ts    ensureSession()：本地 token 的一次性有效性确认
      router/       index / routes（模块注册表）/ guards（登录与角色闸门）/ paths
      layouts/      AppShell.vue：模块导航 + 内容区外壳
      lib/markdown.ts   markdown-it + highlight.js + DOMPurify
      App.vue       RouterView + 掉登录态回登录页的全局 watch
      main.ts       入口：createApp(App).use(router).mount('#app')
      views/
        LoginView.vue   登录页（顶层路由，不在外壳里）
        ChatView.vue    聊天主界面：会话列表 + 消息区 + 输入框 + 思考 / 模型 / 思考强度 + 停止生成
        ProfileView.vue 个人信息（/profile）：资料 + 账号信息 + 使用统计 + 安全区
        admin/UsersView.vue  用户管理（/admin/users）：表格 + 筛选 + 分页 + 多选批量 + 操作弹窗
        admin/AuditView.vue  操作记录（/admin/audit）：只读审计列表
        ForbiddenView.vue /403；NotFoundView.vue 其余路径兜底
      components/
        ConversationSidebar.vue  会话列表：新建 / 删除 / 双击改名
        MessageBubble.vue        消息气泡：助手走 Markdown，用户纯文本 + 图片缩略图 + 思考折叠 + 重新生成
        AttachmentThumb.vue      单图缩略图：有本地 url 直接用，只有 id 时 fetch 字节转 objectURL
        MarkdownContent.vue      Markdown 容器：代码高亮 + 复制按钮 + 流式光标
        ChangePasswordDialog.vue 修改密码；force 模式关不掉（管理员重置过密码）
        SystemPromptDialog.vue   系统提示词（人设），保存后下一条消息生效
        UserMenu.vue             顶栏头像下拉
        ModuleNav.vue            左侧模块导航条（从 routes.ts 派生）+ 底部账号菜单
        common/ConfirmDialog.vue 通用二次确认；requireText 一致才点亮确认（删号用）
        admin/UserFormDialog.vue / ResetPasswordDialog.vue / BatchResetPasswordDialog.vue / BatchResultDialog.vue
        icons/                   导航图标：IconChat / IconUser / IconUsers
      assets/main.css

新增页面 / 平级模块：`views/` 下加组件 + `router/routes.ts` 加一条记录。带 `meta.moduleId` / `title` / `icon` / `order` 会自动进左侧导航；带 `meta.requiresAdmin` 会自动拦成 `/403`，导航组件与守卫都不用改。

## 路由与部署

- history 模式（URL 里没有 `#`）：`/login`、`/chat`、`/403`，其余落 404 页。
- 开发期 Vite 自带回退；**生产部署必须让静态服务器把未命中路径回退到 `index.html`**（nginx：`try_files $uri /index.html`），否则访问或刷新 `/admin/users` 会拿到服务器 404。
- 登录页是顶层路由，其余页面挂在 `layouts/AppShell.vue` 下：左侧 56px 模块导航 + 内容区。

## 平级功能模块（以 /admin/users 为例）

- 一个模块 = `views/<模块>/` + `src/api/<模块>.ts` + `routes.ts` 一条记录；管理端接口在 `/api/admin/**`。
- `meta.requiresAdmin` 的模块：守卫拦成 `/403`、导航条对普通用户隐藏；真正的权限在后端类级 `@RequireAdmin`，前端只是不给人看一个必然 403 的按钮。
- `UserVO.mustChangePassword=true` 时外壳弹**关不掉的改密框**，改完才能用任何模块。
- 管理台视图懒加载，普通用户的 bundle 不含管理台代码。
- `/profile` 是第一个不带 `requiresAdmin` 的平级模块，复用聊天页的 `ChangePasswordDialog` / `SystemPromptDialog`。
- 用户管理的勾选**只限当前页**，翻页 / 搜索 / 刷新即清空；批量结果逐条摊开（失败带后端中文原因），「成功 3 / 失败 1」是正常结果。
- 角色层级决定「谁能管谁」：列表每行带 `canManage`，为 false 或是自己时角色下拉换成纯文本、按钮全部 disabled。指派用的角色下拉取 `/options` 的 `roles`（已按操作者层级过滤），列表的**筛选器**取 `allRoles`（全量）——筛选是读操作，看得见就该筛得出。
- 操作审计是全管理端共用的表：新模块的写操作在自己的事务里调 `AdminAuditService.record()`，界面统一在 `/admin/audit` 看。
