# Chatbot 项目说明文档

> **维护说明**：当项目发生重大架构变动（如新增模块、更换技术栈、调整目录结构、改变鉴权方式等）时，请同步更新本文档，以便后续大模型或开发者快速理解项目。

---

## 一、项目概述

这是一个**全栈 AI 聊天应用**，支持多用户登录、多会话管理、流式对话（SSE），对接阿里云百炼 DashScope（OpenAI 兼容模式）或其他 OpenAI 兼容的大模型服务。

### 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 26 + Spring Boot 4.1.1 + Spring Data JPA + MySQL |
| **前端** | Vue 3.5 + TypeScript 6 + Vite 8 |
| **数据库** | MySQL 8.0（utf8mb4） |
| **LLM 接入** | 阿里云百炼 DashScope（OpenAI 兼容接口），支持流式输出 |

---

## 二、项目目录结构

```
D:\workspace\chatbot\
├── chatbot/                  # 后端项目（Spring Boot）
│   ├── src/main/java/com/chatbot/chatbot/
│   │   ├── ChatbotApplication.java       # 启动类
│   │   ├── auth/                         # 鉴权模块
│   │   ├── config/                       # 配置类
│   │   ├── controller/                   # REST 控制器
│   │   ├── dto/                          # 数据传输对象
│   │   ├── entity/                       # JPA 实体
│   │   ├── llm/                          # LLM 调用层
│   │   ├── repository/                   # 数据访问层
│   │   └── service/                      # 业务逻辑层
│   ├── src/main/resources/
│   │   ├── application.properties        # 主配置
│   │   └── application-local.properties  # 本地覆盖配置
│   ├── sql/
│   │   └── init.sql                      # 建库建表脚本
│   └── pom.xml                           # Maven 配置
│
├── chatbot-web/              # 前端项目（Vue 3）
│   ├── src/
│   │   ├── api.ts                        # API 请求封装
│   │   ├── auth.ts                       # 登录态管理
│   │   ├── types.ts                      # TypeScript 类型定义
│   │   ├── App.vue                       # 根组件（权限闸门）
│   │   ├── main.ts                       # 入口
│   │   ├── components/                   # 可复用组件
│   │   ├── views/                        # 页面视图
│   ├── package.json
│   ├── vite.config.ts
│   └── tsconfig.json
│
├── AGENTS.md                 # 大模型协作说明
├── README.md                 # 仓库首页：一句话简介 + 快速启动
└── PROJECT_OVERVIEW.md       # 本文档：项目全量说明
```

---

## 三、后端模块详解

### 3.1 鉴权模块（auth/）

| 类 | 功能 |
|----|------|
| TokenService | 自签 HMAC-SHA256 token 的签发与校验（类似 JWT 的 HS256，但不依赖 JWT 库）。无状态，不存 session 表。 |
| AuthInterceptor | 登录拦截器：`/api/**` 除登录接口外全部要求 `Authorization: Bearer <token>`。每个请求回表查用户，支持改密码后旧 token 立即失效。 |
| AuthProperties | 鉴权配置属性（token 密钥、有效期、默认管理员账号等）。 |
| CurrentUser | 当前登录用户信息载体，通过 CurrentUserArgumentResolver 注入到控制器方法参数。 |
| RequireAdmin | 注解：标记需要管理员权限的接口。 |
| Roles | 角色常量定义（0=普通用户，1=管理员）。 |

**鉴权流程**：
1. 用户登录 -> TokenService.issue() 签发 token（含 uid/username/role/iat/exp）
2. 后续请求带 `Authorization: Bearer <token>` -> AuthInterceptor 校验签名、有效期、回表查用户状态
3. 改密码后 password_changed_at 更新，旧 token 的 iat 早于它即失效

### 3.2 配置模块（config/）

| 类 | 功能 |
|----|------|
| WebConfig | CORS 配置 + 登录拦截器注册 + CurrentUser 参数解析器注册。白名单只有 /api/auth/login，新接口默认需要登录。 |
| LlmConfig | LLM 客户端 Bean 注册：有 api-key 用 OpenAiCompatibleLlmClient，没有则用 MockLlmClient。 |
| AuthConfig | BCrypt PasswordEncoder Bean 注册（只用 spring-security-crypto 轻量模块）。 |
| AdminUserInitializer | 首次启动兜底：sys_user 表为空时自动创建默认管理员。 |
| GlobalExceptionHandler | 全局异常处理，统一返回 ErrorResponse JSON。 |

### 3.3 控制器层（controller/）

| 控制器 | 路径前缀 | 功能 |
|--------|----------|------|
| AuthController | /api/auth | 登录（POST /login）、获取当前用户（GET /me） |
| ConversationController | /api/conversations | 会话 CRUD：创建、列表、获取消息、删除 |
| ChatController | /api/conversations | 发消息并 SSE 流式返回 AI 回复（POST /{id}/chat） |
| UserController | /api/users | 修改密码（PUT /me/password）、用户列表（GET /，仅管理员） |

### 3.4 实体层（entity/）

| 实体 | 表名 | 说明 |
|------|------|------|
| User | sys_user | 用户表。password 存 BCrypt 哈希，role 为 int（0=普通用户，1=管理员），password_changed_at 用于改密码后使旧 token 失效。 |
| Conversation | conversation | 会话表。title 默认新的对话，首条消息后自动用消息前 30 字作为标题。updated_at 用于会话列表排序。 |
| Message | message | 消息表。role 为枚举（USER/ASSISTANT），content 为 LONGTEXT。与 Conversation 多对一关系。 |
| Role | - | 枚举：USER、ASSISTANT。 |

### 3.5 LLM 调用层（llm/）

| 类 | 功能 |
|----|------|
| LlmClient | 接口：streamChat(messages, enableThinking, listener) 流式调用模型。 |
| OpenAiCompatibleLlmClient | OpenAI 兼容接口实现（百炼 DashScope / DeepSeek / OpenAI 均可）。支持推理模型的 reasoning_content（思考过程）和 content（正式回答）双字段流式解析。 |
| MockLlmClient | 本地 Mock 实现，未配置 api-key 时使用。 |
| LlmProperties | LLM 配置属性：base-url、api-key、model、enable-thinking、request-timeout-seconds、max-history-messages。 |

**SSE 事件类型**（ChatEvent）：
- reasoning：思考过程增量（推理模型 + 开启思考时）
- delta：正式回答增量
- done：生成结束，携带 messageId
- error：生成错误

### 3.6 服务层（service/）

| 服务 | 功能 |
|------|------|
| UserService | 登录（防时序攻击的 BCrypt 比对）、修改密码（换发新 token）、用户列表。 |
| ConversationService | 会话 CRUD、获取消息列表。 |
| ChatService | **核心聊天逻辑**：自动标题、消息入库、取最近 N 条历史调用 LLM、SSE 流式推送、取消/超时处理、部分回复保存。使用虚拟线程执行阻塞式 LLM 调用。 |

### 3.7 数据访问层（repository/）

| Repository | 对应实体 | 自定义查询 |
|------------|----------|------------|
| UserRepository | User | findByUsername |
| ConversationRepository | Conversation | findAllByOrderByUpdatedAtDesc |
| MessageRepository | Message | findByConversationIdOrderByIdAsc、deleteByConversationId |

---

## 四、前端模块详解

### 4.1 核心文件

| 文件 | 功能 |
|------|------|
| App.vue | 根组件 = 权限闸门。未登录显示 LoginView，已登录显示 ChatView。启动时调 /api/auth/me 验证 token 有效性。无 vue-router，页面切换 = 换根组件。 |
| auth.ts | 登录态管理：token/currentUser 为模块级 ref，存 localStorage。isAuthenticated/isAdmin 为 computed。401 时 clearSession() 自动跳回登录页。 |
| api.ts | API 封装：统一挂 Authorization 头、统一处理 401、SSE 流式读取（fetch + ReadableStream）。 |
| types.ts | TypeScript 类型定义，与后端 DTO/VO 对应。 |

### 4.2 页面视图（views/）

| 视图 | 功能 |
|------|------|
| LoginView.vue | 登录页：用户名/密码输入，登录后调 setSession() 存 token。 |
| ChatView.vue | 聊天主界面：会话列表 + 消息区 + 输入框。支持新建/删除会话、流式消息、思考模式开关、停止生成、修改密码、用户管理（管理员）。 |

### 4.3 组件（components/）

| 组件 | 功能 |
|------|------|
| ConversationSidebar.vue | 左侧会话列表：按 updated_at 倒序，支持新建/删除会话。 |
| MessageBubble.vue | 消息气泡：区分 user/assistant，支持 Markdown 渲染、思考过程折叠、流式打字效果。 |
| ChangePasswordDialog.vue | 修改密码弹窗：旧密码 + 新密码，成功后换发新 token。 |
| UserListDialog.vue | 用户管理弹窗（仅管理员）：查看所有用户列表。 |

---

## 五、关键配置说明

### 5.1 后端配置（application.properties）

```properties
# 服务端口
server.port=8089

# 鉴权
auth.token-secret=YOUR_SECRET_HERE  # 生产环境必须用环境变量覆盖
auth.token-ttl-hours=12
auth.default-admin-username=admin
auth.default-admin-password=admin

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/chatbot
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# LLM（百炼 DashScope）
llm.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
llm.api-key=YOUR_API_KEY  # 留空则用 Mock
llm.model=qwen3.6-flash
llm.enable-thinking=true
llm.request-timeout-seconds=900
llm.max-history-messages=20
```

**敏感信息不要提交到仓库**：数据库密码、LLM API Key 等真实密钥放在 `chatbot/src/main/resources/application-local.properties`（已被 git 忽略），启动时加 `--spring.profiles.active=local`；或者直接用环境变量覆盖：

| 环境变量 | 对应配置 | 说明 |
|----------|----------|------|
| `DB_PASSWORD` | `spring.datasource.password` | 数据库密码 |
| `LLM_API_KEY` | `llm.api-key` | 不配置则回退到 MockLlmClient |
| `AUTH_TOKEN_SECRET` | `auth.token-secret` | 少于 32 字符后端拒绝启动 |
| `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD` | `auth.default-admin-username` / `auth.default-admin-password` | 默认管理员账号 |

### 5.2 前端配置（vite.config.ts）

开发时通过 Vite proxy 将 /api 请求代理到后端 http://localhost:8089。

---

## 六、数据库设计

### 6.1 表结构

**sys_user（用户表）**

| 字段 | 说明 |
|------|------|
| `username` | 登录名，唯一约束 `uk_sys_user_username` |
| `password` | BCrypt 哈希（cost 10）。任何接口都不会返回该字段 |
| `role` | `0`=普通用户，`1`=管理员。常量在 `auth/Roles.java`，管理员接口用 `@RequireAdmin` 标记 |
| `created_at` | `datetime(6)` |
| `password_changed_at` | `datetime(6)`，首次改密码前为 `NULL`。签发时间早于它的 token 一律失效，所以改密码会踢掉其他所有会话 |

**conversation（会话表）**：`title`（首条消息后自动取消息前 30 字）、`created_at`、`updated_at`（会话列表按它倒序）。

**message（消息表）**：`conversation_id` 外键、`role` 枚举（USER/ASSISTANT）、`content` LONGTEXT、`created_at`。

### 6.2 初始化

执行 `chatbot/sql/init.sql` 建库建表，或直接启动后端（ddl-auto=update 自动建表，AdminUserInitializer 自动创建默认管理员）。

`init.sql` 用 `INSERT IGNORE` 写入默认管理员，所以脚本可重复执行，且不会覆盖已经被改过的密码；`config/AdminUserInitializer.java` 在 `sys_user` 表为空时兜底重建该账号。默认 `admin` / `admin`，可用 `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD` 覆盖。

---

## 七、API 接口汇总

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | /api/auth/login | 无 | 登录，返回 token + 用户信息 |
| GET | /api/auth/me | 需要 | 获取当前登录用户（验证 token 有效性） |
| POST | /api/conversations | 需要 | 创建新会话 |
| GET | /api/conversations | 需要 | 获取会话列表（按 updated_at 倒序） |
| GET | /api/conversations/{id}/messages | 需要 | 获取指定会话的消息列表 |
| DELETE | /api/conversations/{id} | 需要 | 删除会话及其所有消息 |
| POST | /api/conversations/{id}/chat | 需要 | 发消息，SSE 流式返回 AI 回复 |
| PUT | /api/users/me/password | 需要 | 修改自己的密码，返回新 token |
| GET | /api/users | 管理员 | 获取所有用户列表 |

**几个接口的请求/响应细节**：

- `POST /api/auth/login`：请求 `{username, password}`，响应 `{token, tokenType, expiresIn, user}`。用户名不存在和密码错误返回同一句 `401` 文案。
- `GET /api/auth/me`：前端启动时调用，用来恢复本地会话。
- `PUT /api/users/me/password`：请求 `{oldPassword, newPassword}`，响应是一份新的 `LoginResponse`（含新 token），所以改密码不会踢掉当前会话。
- `GET /api/users`：`@RequireAdmin`，普通用户返回 `403`。
- 前端把任何 `401` 都当作「会话已失效」，直接切回登录页，因此不存在绕过登录就能访问的页面。

---

## 八、核心流程

### 8.1 聊天流程

```
用户输入 -> ChatController.chat()
  -> ChatService.chat()
    -> 自动标题（首条消息前30字）
    -> 用户消息入库
    -> 取最近 N 条历史
    -> 虚拟线程异步调用 LlmClient.streamChat()
    -> SSE 推送 reasoning/delta 事件
    -> 完成后助手消息入库，推送 done 事件
```

### 8.2 鉴权流程

```
登录 -> TokenService.issue() -> 返回 token
请求 -> AuthInterceptor.preHandle()
  -> 解析 Authorization 头
  -> TokenService.verify() 校验签名+有效期
  -> 回表查用户（账号存在？改过密码？）
  -> 注入 CurrentUser 到 request attribute
  -> CurrentUserArgumentResolver 解析到控制器参数
```

---

## 九、安全设计要点

1. **密码**：BCrypt（cost 10）哈希存储，从不存明文，任何接口都不返回该字段。登录时对「用户不存在」和「密码错」返回同一句话，防用户名枚举。
2. **密码策略**：新密码长度 6-64 且不能与旧密码相同；`oldPassword` 不设长度下限——种子管理员密码 `admin` 只有 5 位，加了下限就没人能改密码了。
3. **Token 格式**：无状态自签，`base64url(payloadJson).base64url(HMAC-SHA256)`，密钥 `auth.token-secret`（少于 32 字符后端拒绝启动），有效期 `auth.token-ttl-hours`（默认 12 小时），请求头 `Authorization: Bearer <token>`。
4. **改密码即失效**：payload 里的 `iat` 用 epoch **毫秒**而不是秒，这样「同一秒内签发的旧 token」和「改密码后换发的新 token」也能区分开；`AuthInterceptor` 比对 `iat` 与 `password_changed_at`，早于后者的 token 立即失效。
5. **防时序攻击**：登录时用预计算的 dummyHash 做 BCrypt 比对，避免「用户不存在」时响应更快。
6. **权限**：白名单模式，新接口默认需要登录。管理员接口用 @RequireAdmin 注解，普通用户访问返回 403。
7. **SSE 安全**：用 fetch 而非 EventSource（支持 POST + 自定义头），token 不放 URL 查询参数。

---

## 十、开发指南

### 10.1 启动后端

```bash
cd chatbot
# 确保 MySQL 已启动，创建 chatbot 数据库（或依赖 ddl-auto=update）
# 配置 LLM API Key（二选一）：
#   方式1：编辑 src/main/resources/application-local.properties，加 llm.api-key=xxx
#   方式2：设置环境变量 LLM_API_KEY=xxx
./mvnw spring-boot:run
# 访问 http://localhost:8089
```

### 10.2 启动前端

```bash
cd chatbot-web
npm install
npm run dev
# 访问 http://localhost:5173（Vite 代理 /api 到后端 8089）
```

### 10.3 默认管理员

- 用户名：admin
- 密码：admin
- **首次登录后请立即修改密码**

---

## 十一、扩展点

1. **新增角色**：修改 Roles 常量 + Role 枚举，添加新注解（如 @RequireOperator），在 AuthInterceptor 中判断。
2. **切换 LLM 服务商**：修改 llm.base-url 和 llm.model，任何 OpenAI 兼容接口均可直连。
3. **添加新接口**：在 controller/ 下新增控制器，默认需要登录。需要管理员权限加 @RequireAdmin。
4. **前端新增页面**：在 views/ 下新增组件，在 App.vue 中根据条件切换渲染（无 router）。

---

## 十二、已知限制与注意事项

1. **无分页**：会话列表和消息列表目前全量加载，数据量大时需加分页。
2. **无 WebSocket**：SSE 为单向推送，客户端断开后无法恢复，需重新发起请求。
3. **无文件上传**：目前仅支持文本对话。
4. **Mock 模式**：未配置 api-key 时使用 MockLlmClient，返回固定回复，仅用于调试。
5. **表结构同步**：修改实体类后，需同步更新 sql/init.sql（Hibernate 只补缺不改动已有列）。

---

*最后更新：2026-09-08*