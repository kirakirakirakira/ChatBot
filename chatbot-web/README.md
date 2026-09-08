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

改密码成功后后端会换发新 token（响应体就是一份新的 `LoginResponse`），前端用它覆盖本地 token，当前会话不中断。

## SSE 流式解析

后端的 chat 接口是 **POST + SSE**，浏览器原生 `EventSource` 只支持 GET，**用不了**。
必须用 `fetch` + `ReadableStream` 自己解析，三个要点：

1. 按**空行**切帧，每帧去掉 `data: ` 前缀后再 `JSON.parse`
2. `TextDecoder` 必须开 `stream: true`——否则一个多字节中文字符被 chunk 切成两半时会解出乱码
3. token 放请求头，**不要放 URL 查询参数**（会进各种访问日志）

事件契约（reasoning / delta / done / error 的形状与语义）由后端定义，见 [chatbot/README.md](../chatbot/README.md)。
前端侧的约定：

- `reasoning` 是思考过程，不属于消息正文，后端也不入库。收到第一帧就显示「思考中…」，做成可折叠
- 收到第一帧 `delta` 再切到正文渲染
- 遇到不认识的 `type` 直接忽略，保持向后兼容
- **停止生成 = abort 这个 fetch**，不需要调额外接口；后端会停止调用模型并把已生成的部分入库，刷新页面能看到
- 空闲超过 60 秒连接会被中间层掐断，正常情况下后端转发思考帧会一直有字节流动

`MessageBubble.vue` 负责 Markdown 渲染、思考过程折叠和流式打字效果。

## 目录结构

    src/
      api.ts        请求封装：鉴权头、401 处理、SSE 读流
      auth.ts       登录态：token / currentUser / isAdmin
      types.ts      类型定义，与后端 DTO / VO 一一对应
      App.vue       根组件 = 权限闸门
      main.ts       入口
      views/
        LoginView.vue   登录页
        ChatView.vue    聊天主界面：会话列表 + 消息区 + 输入框 + 思考开关 + 停止生成
      components/
        ConversationSidebar.vue   会话列表，按 updated_at 倒序，支持新建 / 删除
        MessageBubble.vue         消息气泡，区分 user / assistant
        ChangePasswordDialog.vue  修改密码，所有人可见
        UserListDialog.vue        用户管理，仅 isAdmin 时可见
      assets/main.css

新增页面：在 `views/` 下加组件，在 `App.vue` 里按条件切换渲染（没有 router）。