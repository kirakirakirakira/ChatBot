# Chatbot 项目全景说明（PROJECT_OVERVIEW.md）

> **本文件是这个仓库的唯一架构事实源。** 接手本项目的大模型或新成员，请先通读本文，再去检索代码——
> 绝大多数「某个功能在哪、某张表什么结构、某个接口什么形状」的问题，本文都能直接回答。
>
> **维护铁律**：任何一次代码改动（增删改文件、改接口、改表结构、改配置项、改鉴权方式）之后，
> 必须同步更新本文对应章节，并刷新文末「最后更新」。需要联动更新的完整清单见第十一节。

---

## 文档分工（四份，各写各的，不要互相复述）

| 文档 | 定位 | 只写什么 |
|---|---|---|
| `README.md`（仓库根） | 门面 | 一句话简介 + **唯一的启动步骤** |
| `PROJECT_OVERVIEW.md`（本文） | 架构事实源 | 目录结构、逐文件清单、模块职责、数据模型、API 全表、核心流程、设计决策、已知限制 |
| `chatbot/README.md` | 后端契约 | SSE 事件形状与语义、思考转发原因、取消语义、错误响应体、配置取舍 |
| `chatbot-web/README.md` | 前端约定 | Vite 代理、Node 版本、登录闸门、SSE 解析要点、组件地图 |

另有仓库根的 `AGENTS.md`：给大模型的协作规则（检索前先读本文、改完代码同步本文）。
**注意它被 `.gitignore` 第 78 行忽略，只在本地工作区生效，不会进版本库。**

---

## 〇、五分钟速览

一个类 ChatGPT 的全栈 AI 聊天应用：多用户登录 + 多会话管理 + SSE 流式对话，模型走阿里云百炼 DashScope（OpenAI 兼容接口）。

| 项目 | 事实 |
|---|---|
| 仓库根 | `D:/workspace/chatbot`（两个平级子项目，**不是** Maven/npm 多模块工程，没有父 pom、没有 workspace） |
| 后端 | `chatbot/`，Spring Boot 4.1.1 + Java 26 + Maven，包根 `com.chatbot.chatbot`，端口 **8089** |
| 前端 | `chatbot-web/`，Vue 3.5 + TypeScript 6 + Vite 8，端口 **5173**，`/api` 代理到 8089 |
| 数据库 | MySQL 8，库名 `chatbot`，共 **4 张表**：`sys_user` / `conversation` / `message` / `attachment`（图片字节直接存 LONGBLOB） |
| LLM | `llm.api-key` 有值 → `OpenAiCompatibleLlmClient`；为空 → `MockLlmClient`（本地假回复，链路照样能跑通） |
| 鉴权 | 自签 HMAC-SHA256 token（不用 JWT 库、不用 Spring Security starter），白名单**只有** `POST /api/auth/login` |
| 默认账号 | `admin` / `admin`（`sql/init.sql` 的 `INSERT IGNORE` 与 `AdminUserInitializer` 二选一兜底创建） |

**本项目刻意没有的东西**（找不到不是你没找到，是真的没有）：

- 没有状态管理库（Pinia/Vuex）：登录态是 `src/auth.ts` 里的模块级 `ref`。
- 没有 `axios`：全部用原生 `fetch`。
- 没有 `spring-boot-starter-security`：只引了 `spring-security-crypto` 拿 BCrypt。
- 没有 WebSocket：流式回复用 SSE（`SseEmitter`）。
- 没有 offset 分页 / 跳页：消息列表是**游标式**分页（只能一页一页往前翻，聊天界面也只需要往前翻）；会话列表与用户列表仍全量查询。
- 没有单元测试：`src/test` 下只有一个空的 `contextLoads()`，而且要真 MySQL 才跑得起来；SSE 链路只能靠 `chatbot/README.md` 里的 curl 命令手测。

**检索入口速查**（想改 X → 直接看 Y）：

| 我想… | 看这里 |
|---|---|
| 加/改一个 REST 接口 | `chatbot/src/main/java/com/chatbot/chatbot/controller/` + 对应 `service/` + `dto/`，前端再改 `chatbot-web/src/api.ts` |
| 改 SSE 事件形状 | `dto/ChatEvent.java`（后端产出）+ `chatbot-web/src/api.ts` 的 `dispatchEvent()` + `types.ts` 的 `ChatStreamEvent` |
| 改登录/权限 | `auth/` 整个包 + `config/WebConfig.java`（白名单在这里）+ 前端 `src/auth.ts`、`src/App.vue` |
| 改表结构 | `entity/` 四个实体类（**唯一事实源**）→ 同步 `chatbot/sql/init.sql` |
| 改图片上传 / 多模态 | 后端 `service/AttachmentService.java` + `entity/Attachment.java` + `llm/LlmContentPart.java`；前端 `components/AttachmentThumb.vue` + `ChatView.vue` 的 `pending` 状态 |
| 换模型/换服务商 | `chatbot/src/main/resources/application.properties` 的 `llm.*` + `llm/LlmProperties.java` |
| 改 LLM 请求体/解析 | `llm/OpenAiCompatibleLlmClient.java` |
| 改流式编排、取消、自动标题、历史截断 | `service/ChatService.java`（全项目最复杂的一个类） |
| 改界面配色/圆角/间距 | `chatbot-web/src/assets/main.css`（CSS 变量 + 登录/弹窗共用件），组件内 `<style scoped>` |
| 改聊天主界面行为 | `chatbot-web/src/views/ChatView.vue`（前端最大的文件，约 586 行） |
| 加配置项 | `application.properties` → 对应 `*Properties` record（`AuthProperties` / `LlmProperties`）→ 第五节表格 |
| 改端口/代理 | 后端 `application.properties` 的 `server.port`，前端 `chatbot-web/vite.config.ts` 的 `server.proxy` |

---

## 一、项目概述与技术栈

全栈 AI 聊天应用：多用户登录、多会话管理、流式（SSE）对话，对接阿里云百炼 DashScope（OpenAI 兼容模式）或任何其他 OpenAI 兼容模型服务。

> **「多用户」= 登录与用户管理 + 会话数据隔离。** 每个会话归属一个用户（`conversation.owner_id`），列表、读取、删除、发消息都只作用于自己的会话；管理员**没有**跨用户查看的特权（见第九节第 15 条）。

| 层级 | 技术与精确版本 | 出处 |
|---|---|---|
| 后端语言 | Java **26** | `chatbot/pom.xml` 的 `<java.version>` |
| 后端框架 | Spring Boot **4.1.1**（`spring-boot-starter-parent`） | `chatbot/pom.xml` |
| 后端依赖 | `starter`、`starter-web`、`starter-data-jpa`、`starter-validation`、`mysql-connector-j`(runtime)、`spring-security-crypto`、`starter-test`(test) | `chatbot/pom.xml` |
| JSON | Spring Boot 4 自带的 **Jackson 3**：注入类型是 `tools.jackson.databind.ObjectMapper`；注解仍用 `com.fasterxml.jackson.annotation.*`（Jackson 3 兼容 Jackson 2 注解包） | `TokenService` / `ChatService` / `OpenAiCompatibleLlmClient` / `ChatEvent` |
| HTTP 客户端 | JDK 原生 `java.net.http.HttpClient`（**没有** RestTemplate/WebClient/OkHttp） | `llm/OpenAiCompatibleLlmClient.java` |
| 数据库 | MySQL 8.0，`utf8mb4` / `utf8mb4_unicode_ci` | `chatbot/sql/init.sql` |
| ORM | Spring Data JPA + Hibernate，`ddl-auto=update`，`open-in-view=false` | `application.properties` |
| 前端 | Vue **3.5.40**、**vue-router ^4**（URL 即模块；权限闸门在路由守卫里）、markdown-it **^15**（渲染）、highlight.js **^11**（只注册 common 语言子集）、dompurify **^3**（渲染结果消毒） | `chatbot-web/package.json` |
| 前端工具链 | `@vitejs/plugin-vue` ^6.0.8、`vite-plugin-vue-devtools` ^8.1.5、`vue-tsc` ^3.3.7、`npm-run-all2` ^9.0.2、`@vue/tsconfig` ^0.9.1、`@tsconfig/node24`、`@types/node` ^24.13.3、`@types/markdown-it` ^14 | `chatbot-web/package.json` |
| Node 要求 | `^22.18.0 \|\| >=24.12.0`（低版本直接 EBADENGINE） | `chatbot-web/package.json` 的 `engines` |
| Maven Wrapper | 3.3.4（`only-script`，Apache Maven 3.9.16） | `chatbot/.mvn/wrapper/maven-wrapper.properties` |
| LLM | 百炼 DashScope OpenAI 兼容模式，`qwen3.6-flash`，`stream=true` | `application.properties` 的 `llm.*` |


---

## 二、仓库结构与完整文件清单

### 2.1 目录树

```
chatbot/                        # 仓库根（git 仓库在这一层）
├── AGENTS.md                   # 大模型协作规则（gitignore，仅本地）
├── README.md                   # 门面 + 唯一启动步骤
├── PROJECT_OVERVIEW.md         # 本文
├── .gitignore                  # 根级，覆盖两个子项目
│
├── chatbot/                    # ← 后端：Spring Boot + Maven
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd         # Maven Wrapper（.gitattributes 锁定 mvnw=lf、*.cmd=crlf）
│   ├── README.md               # 后端契约文档
│   ├── sql/init.sql            # 建库建表 + 种子管理员
│   └── src/
│       ├── main/java/com/chatbot/chatbot/
│       │   ├── ChatbotApplication.java
│       │   ├── auth/           # 7 个文件：token 签发校验 + 拦截器 + 当前用户注入 + 角色
│       │   ├── config/         # 6 个文件：Web/CORS、BCrypt、LLM Bean、种子管理员、全局异常
│       │   ├── controller/     # 6 个文件：Auth / Conversation / Chat / User / Llm / Attachment
│       │   ├── dto/            # 15 个文件：请求体、响应 VO、SSE 事件、统一错误体
│       │   ├── entity/         # 5 个文件：User / Conversation / Message / Attachment / Role
│       │   ├── llm/            # 8 个文件：客户端抽象 + OpenAI 兼容实现 + Mock + 配置 + 调用选项 + 多模态 content 段
│       │   ├── repository/     # 4 个文件：Spring Data JPA 接口
│       │   └── service/        # 4 个文件：ChatService / ConversationService / UserService / AttachmentService
│       ├── main/resources/
│       │   ├── application.properties          # 主配置（提交进仓库）
│       │   └── application-local.properties    # 本地真实密钥（gitignore）
│       └── test/java/com/chatbot/chatbot/ChatbotApplicationTests.java
│
└── chatbot-web/                # ← 前端：Vue 3 + Vite + TypeScript
    ├── package.json / package-lock.json
    ├── vite.config.ts          # /api 代理 + @ 别名 + devtools 插件
    ├── tsconfig.json / tsconfig.app.json / tsconfig.node.json
    ├── env.d.ts / index.html / README.md
    ├── public/favicon.ico
    └── src/
        ├── main.ts             # createApp(App).use(router).mount('#app')
        ├── App.vue             # RouterView + 掉登录态回登录页的全局 watch
        ├── session.ts          # ensureSession()：本地 token 的一次性有效性确认
        ├── router/             # index / routes（模块注册表）/ guards（登录与角色闸门）/ paths
        ├── layouts/            # AppShell.vue：模块导航 + 内容区外壳
        ├── api.ts              # 聊天与登录的接口清单（传输层已抽到 api/client.ts）
        ├── api/                # client.ts：fetch 封装 + Authorization 头 + 401 兜底，全站唯一一份
        ├── auth.ts             # 登录态（模块级 ref + localStorage）
        ├── types.ts            # 与后端 DTO/VO 一一对应的类型
        ├── assets/main.css     # 全局 CSS 变量 + 登录/弹窗共用件
        ├── lib/                # markdown.ts：markdown-it + highlight.js + DOMPurify
        ├── views/              # LoginView、ChatView、ForbiddenView、NotFoundView
        └── components/         # ConversationSidebar、MessageBubble、MarkdownContent、AttachmentThumb、ChangePasswordDialog、SystemPromptDialog、UserListDialog、UserMenu、ModuleNav、icons/
```

### 2.2 后端逐文件清单

包根：`com.chatbot.chatbot`，路径前缀统一为 `chatbot/src/main/java/com/chatbot/chatbot/`。

**根**

| 文件 | 职责 |
|---|---|
| `ChatbotApplication.java` | `@SpringBootApplication` 启动类，仅此而已。组件扫描默认覆盖 `com.chatbot.chatbot.**`。 |

**auth/ — 鉴权（自签 token，不依赖 Spring Security）**

| 文件 | 职责 |
|---|---|
| `TokenService.java` | token 的签发与校验。格式 `base64url(payloadJson) + "." + base64url(HMAC-SHA256签名)`，思路同 JWT HS256 但不引 JWT 库，只用 JDK `Mac` + Jackson。内部 record `TokenPayload(long uid, String username, int role, long iat, long exp)`；`iat`/`exp` 用 **epoch 毫秒**（秒级精度分不开「改密码那一秒签发的旧 token」）。密钥短于 `MIN_SECRET_LENGTH = 32` 字符时**构造函数直接抛异常、后端启动失败**。签名比较用 `MessageDigest.isEqual` 常量时间比对。对外方法：`issue(User)`、`verify(String)`、`ttlSeconds()`。 |
| `AuthInterceptor.java` | `HandlerInterceptor.preHandle`。① 非 `HandlerMethod`（CORS 预检、静态资源、404）直接放行；② 只认 `Authorization: Bearer <token>`，**不做 `?token=` 兜底**（会进访问日志）；③ `TokenService.verify()` 校签名与有效期；④ **回表查一次用户**（`userRepository.findById`），让删号/改角色/改密码立刻生效；⑤ 比较 `payload.iat()` 与 `user.passwordChangedAt`（都转 epoch 毫秒），旧 token 作废；⑥ `@RequireAdmin` 检查（方法级或类级注解）；⑦ 把 `CurrentUser` 放进 request attribute，key = 常量 `CURRENT_USER_ATTRIBUTE = "chatbot.currentUser"`。401 与 403 都抛 `ResponseStatusException`。 |
| `AuthProperties.java` | `@ConfigurationProperties(prefix = "auth")` 的 record：`tokenSecret`、`tokenTtlHours`(默认12)、`defaultAdminUsername`(默认 admin)、`defaultAdminPassword`(默认 admin)。由 `AuthConfig` 上的 `@EnableConfigurationProperties` 启用。 |
| `CurrentUser.java` | record `(Long id, String username, Integer role)` + `isAdmin()`。**刻意不用 ThreadLocal**：`/chat` 是 SSE 异步接口，请求线程与生成线程不是同一个。 |
| `CurrentUserArgumentResolver.java` | `HandlerMethodArgumentResolver`，让控制器方法直接声明 `CurrentUser` 形参。从 request attribute 取；取不到抛 401（兜底，正常走不到）。 |
| `RequireAdmin.java` | 注解，`@Target({TYPE, METHOD})` + `RUNTIME`。权限判断统一在 `AuthInterceptor`，不在业务层重复。 |
| `Roles.java` | 角色常量：`USER = 0`、`ADMIN = 1`；`isAdmin(Integer)`；`label(Integer)` 返回中文名（管理员/普通用户）。`sys_user.role` 存 **int 而非字符串/ENUM**，以后加角色不用改列类型。 |

**config/ — 装配与全局行为**

| 文件 | 职责 |
|---|---|
| `WebConfig.java` | `WebMvcConfigurer`。① CORS：`/api/**`，白名单来自 `CorsProperties`（默认只有 `http://localhost:5173`，**刻意不用 `*`**），方法 GET/POST/PUT/DELETE/OPTIONS，头 `*`；② 拦截器：`addPathPatterns("/api/**").excludePathPatterns(PUBLIC_PATHS)`，**`PUBLIC_PATHS` 只有 `/api/auth/login` 一项**（白名单模式：新接口默认要登录）；③ 注册 `CurrentUserArgumentResolver`。改免登录路径就改这个文件的常量。 |
| `AuthConfig.java` | `@EnableConfigurationProperties(AuthProperties.class)` + 声明 `PasswordEncoder` Bean = `BCryptPasswordEncoder()`（默认 strength 10）。因为没引 starter-security，这个 Bean 必须自己声明。 |
| `CorsProperties.java` | `cors.*` 配置 record。`origins()` 去掉空白项后返回白名单数组；**全空白抛 `IllegalStateException` 让启动失败**，不退化成「全开」——CORS 配漏了的默认结果不该是 `*` |
| `LlmConfig.java` | `@EnableConfigurationProperties(LlmProperties.class)` + 声明 `LlmClient` Bean：`apiKey` 为空/空白 → `MockLlmClient`，否则 → `OpenAiCompatibleLlmClient`。**Bean 在启动时定型，改 key 必须重启。** |
| `AdminUserInitializer.java` | `ApplicationRunner`。`sys_user` 表 `count() == 0` 时创建初始管理员（用户名/密码取 `AuthProperties`）。很多人不跑 `init.sql`、直接靠 `ddl-auto=update` 启动，那样表建出来但没账号，谁都登不进去。条件只有 count==0，不会覆盖已有账号或重置改过的密码。日志里**故意不打印密码**。 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice`。处理 `ResponseStatusException`（保留状态码，reason 进 message）、`MethodArgumentNotValidException`（拼字段错误 → 400）、`HttpMessageNotReadableException`（→ 400「请求体不是合法的 JSON」）。另接 `MaxUploadSizeExceededException`（→ 413 中文文案；multipart 在解析阶段就抛、到不了 controller 里那道 5MB 校验）与 `MissingServletRequestPartException`（→ 400「缺少上传字段」）。统一返回 `ErrorResponse`。**两个关键取舍**：① 刻意不加 catch-all `Exception` 处理器——`/chat` 的错误已在异步线程里用 `ChatEvent.error` 发给前端，catch-all 会往已提交的 `text/event-stream` 里再写一份 JSON；② 每个 handler 都显式 `contentType(APPLICATION_JSON)`，否则内容协商因 `Accept: text/event-stream` 变成 406，把真实状态码盖掉。 |

**controller/ — REST 入口（薄，只做参数绑定与委派）**

| 文件 | 前缀 | 端点 |
|---|---|---|
| `AuthController.java` | `/api/auth` | `POST /login`（`@Valid LoginRequest` → `LoginResponse`；全站唯一免登录接口）、`GET /me`（`CurrentUser` → `UserVO`） |
| `ConversationController.java` | `/api/conversations` | `POST`（→ 201 + `ConversationVO`）、`GET`（→ `List<ConversationVO>`）、`GET /{id}/messages?before=&limit=`（→ `MessagePageVO`，items 里带附件元信息）、`PUT /{id}/title`（→ `ConversationVO`）、`DELETE /{id}`（→ 204）、`POST /{id}/attachments`（multipart 字段名 `file` → 201 + `AttachmentVO`）。`ChatController` 另有 `POST /{id}/regenerate`（SSE，请求体可省略） |**每个方法都声明 `CurrentUser` 形参**，归属校验在 service 层 |
| `AttachmentController.java` | `/api/attachments` | `GET /{id}` → 图片字节（`Content-Type` = 存的 mime + `X-Content-Type-Options: nosniff`）。**没有类级 `@RequestMapping`**：只有一个方法、且路径不在 `/api/conversations` 下 |
| `ChatController.java` | `/api/conversations` | `POST /{id}/chat`，`produces = TEXT_EVENT_STREAM_VALUE`，`@Valid ChatRequest` + `CurrentUser` → `SseEmitter`。**CurrentUser 必须在进 service 之前解析**：生成跑在虚拟线程上，那里拿不到 request attribute，补不了归属校验 |
| `LlmController.java` | `/api/llm` | `GET /options`（→ `LlmOptionsVO`）。默认受保护：模型清单不敏感，但没必要在未登录时暴露部署用了哪些模型 |
| `UserController.java` | `/api/users` | `PUT /me/password`（→ 新的 `LoginResponse`）、`PUT /me/system-prompt`（→ `UserVO`，**不换发 token**：改人设不作废登录态）、`GET`（`@RequireAdmin` → `List<UserVO>`，不含 systemPrompt）。路径里的 `me` 就是「只能是自己」，**不接受 userId 参数**。 |

> 注意：会话类控制器（`ConversationController` / `ChatController` / `AttachmentController`）**每个方法都声明 `CurrentUser` 形参**，归属校验统一在 service 层，查不到或不是自己的一律 404（理由见第九节）。2026-09-24 之前的旧文档写「没有 `CurrentUser` 形参」，那是会话隔离落地前的状态，已修正（见 13.3）。

**dto/ — 全部是 record，请求体带 jakarta.validation 注解**

| 文件 | 形状 / 校验 |
|---|---|
| `LoginRequest.java` | `(username, password)`，各 `@NotBlank`。**刻意不加 `@Size`/`@Pattern`**：格式校验只会把「密码错了」变成「格式不合法」，白给爆破的人送信息。 |
| `LoginResponse.java` | `(token, tokenType="Bearer", expiresIn秒, user)`，静态工厂 `of(token, expiresIn, user)`。登录与改密码共用。 |
| `ChangePasswordRequest.java` | `(oldPassword @NotBlank, newPassword @NotBlank @Size(6..64))`。`oldPassword` **只有 `@NotBlank`、不限长度**：种子管理员密码 `admin` 只有 5 位，给它套 `min=6` 会让人永远改不了密码。 |
| `ChatRequest.java` | `(message, enableThinking, model, thinkingBudget, enableSearch, attachmentIds)`。`message` **刻意没有 `@NotBlank`**：纯图片提问合法，「文本和图片不能同时为空」在 `ChatService` 里判、报同一个 400。其余字段为 null 时各自沿用服务端默认 / 不下发 |
| `AttachmentVO.java` | `(id, mime, fileName, size)`：附件元信息，**不含字节**。上传接口的响应、`MessageVO.attachments` 的元素都是它；字节另走 `GET /api/attachments/{id}` |
| `ChatEvent.java` | SSE 事件体 `(type, content, messageId, model, promptTokens, completionTokens, reasoningTokens)` + `@JsonInclude(NON_NULL)`。静态工厂：`reasoning(content)` / `delta(content)` / `done(saved)`（把入库消息的模型与用量一起带上）/ `error(content)`。**错误文案在 `content`，不是 `message` 字段。** |
| `UserVO.java` | `(id, username, role, roleLabel, createdAt)`。**没有 password 字段**：BCrypt 哈希也不能出网。`roleLabel` 由后端 `Roles.label()` 给出，前端不用再维护 0/1 映射。 |
| `ConversationVO.java` | `(id, title, createdAt, updatedAt)` |
| `MessageVO.java` | `(id, role小写字符串, content, reasoning, model, promptTokens, completionTokens, reasoningTokens, attachments, createdAt)`。`attachments` 是 `List<AttachmentVO>`，**没图时传 null 而不是空数组**（NON_NULL 会把整个字段省掉）。record 上 `@JsonInclude(NON_NULL)`：没有的字段干脆不下发 |
| `RenameConversationRequest.java` | `(title)`，`@NotBlank` + `@Size(max=100)`（100 是 `conversation.title` 列宽） |
| `UpdateSystemPromptRequest.java` | `(systemPrompt)`，`@Size(max=2000)`；全空白 = 清除人设 |
| `RegenerateRequest.java` | `(enableThinking, model, thinkingBudget)`，整个体可省略；model 不传时沿用被删回答的模型 |
| `LlmOptionsVO.java` | `(models, defaultModel, visionModels)`：界面模型选择器的数据源。`visionModels` 来自 `llm.vision-models`（`models` 的子集），前端靠它决定显不显示「上传图片」按钮 |
| `RegenerateRequest.java` | `(enableThinking, model, thinkingBudget, enableSearch)`，整个体可省略（`@RequestBody(required = false)`）；语义与 `ChatRequest` 同名字段一致。**没有 attachmentIds**：重跑用的图片已经挂在原用户消息上，从历史里读 |
| `MessagePageVO.java` | `(items: List<MessageVO>, beforeId, hasMore)`。消息分页响应：items 已按时间正序，`beforeId` 是往前翻的游标（null = 没有更早的）。**刻意不含 total / 总页数**：算总数要在 LONGTEXT 大表上多跑一次 `count(*)`，每翻一页跑一次不值 |
| `ErrorResponse.java` | `(timestamp, status, error, message, path)`，字段与 Spring 默认 `/error` 输出一致。 |

**entity/ — JPA 实体，表结构的唯一事实源**

| 文件 | 表 | 要点 |
|---|---|---|
| `User.java` | `sys_user` | 表名用 `sys_user` 而不是 `user`（MySQL 关键字）。字段 `id`、`username`(唯一)、`password`(BCrypt)、`role`、`createdAt`、`passwordChangedAt`(可空)、`systemPrompt`(可空 TEXT：该用户的人设，每轮作为 system 消息放在历史最前面) |
| `Conversation.java` | `conversation` | `id`、`owner`（`@ManyToOne` LAZY 非空，`@JoinColumn(name="owner_id")`，外键名 `fk_conversation_owner`）、`title`(非空,100)、`createdAt`、`updatedAt`。表上 `@Index idx_conversation_owner_updated(owner_id, updated_at)` 服务「按用户查列表 + updated_at 倒序」这一条查询。取单个会话只走 `ConversationRepository.findByIdAndOwnerId`，**直接用 findById 就是越权** |
| `Message.java` | `message` | `id`、`conversation`(`@ManyToOne` LAZY, 非空)、`role`(`@Enumerated(STRING)`, 长度16)、`content`(LONGTEXT)、`reasoning`(可空 LONGTEXT，思考全文)、`model`(varchar 64，回答用的模型)、`promptTokens` / `completionTokens` / `reasoningTokens`(可空 int，用量；拿不到就 NULL，不填 0 冒充真实值)、`createdAt` |
| `Attachment.java` | `attachment` | 图片附件：`id`、`conversation`(`@ManyToOne` LAZY 非空，外键 `fk_attachment_conversation`，**RESTRICT**)、`messageId`(可空 Long，**刻意不做 `@ManyToOne`**：附件先于消息存在；NULL = 传了还没发出去的孤儿)、`mime`(白名单 5 种)、`fileName`、`sizeBytes`(列名带 `_bytes`：JPQL 里 `size` 是保留函数名)、`data`(`LONGBLOB`)、`createdAt`。一个附件只属于一条消息，不变式由 `AttachmentRepository.linkToMessage` 的 `message_id IS NULL` 条件保证 |
| `Role.java` | — | 枚举 `USER`、`ASSISTANT`。**加新值（如 SYSTEM）时已存在的库不会自动变更列类型**，需手工 `ALTER TABLE message MODIFY role enum(...)`（`init.sql` 末尾有备注）。 |

**llm/ — 模型调用抽象与实现**

| 文件 | 职责 |
|---|---|
| `LlmClient.java` | 接口，唯一方法 `streamChat(List<LlmMessage> messages, LlmCallOptions options, LlmStreamListener listener)`。**阻塞方法**，调用方应在虚拟线程中执行；正常返回=生成结束，失败抛异常 |
| `LlmCallOptions.java` | record `(model, enableThinking, thinkingBudget, enableSearch)`：单次调用的模型 / 思考开关 / 思考预算 / 联网搜索。可选项收进 record，以后加参数不用改接口签名 |
| `LlmStreamListener.java` | 回调接口：`onToken(String)`（必须实现，正式回答增量）+ `onReasoning(String)`、`onUsage(int, int, Integer)`（均 `default` 空实现：思考增量、本轮用量） |
| `LlmMessage.java` | record `(role, content, reasoningContent)`，OpenAI 格式。`reasoningContent` 标 `@JsonProperty("reasoning_content")` + NON_NULL：qwen3.8-max / qwen3.8-flash 的 preserve_thinking 默认 true，要求历史 assistant 消息把思考完整回传 |
| `LlmProperties.java` | `@ConfigurationProperties(prefix="llm")` record：`baseUrl`、`apiKey`、`model`（**只是默认值，请求可覆盖**）、`maxHistoryMessages`(默认20，<=0 不限)、`maxHistoryTokens`(默认24000，<=0 不限)、`enableThinking`(Boolean，可为 null)、`requestTimeoutSeconds`(默认900)、`availableModels`(界面可选模型白名单) |
| `OpenAiCompatibleLlmClient.java` | 真实实现。`POST {baseUrl}/chat/completions`，body `{model(来自 options), stream:true, messages, stream_options:{include_usage:true}, enable_thinking?, thinking_budget?, enable_search?}`；头 `Authorization: Bearer <apiKey>`。**usage 只在流式最后一帧返回且该帧 choices 为空**，解析必须放在「空 choices 就跳过」之前，否则永远拿不到用量。逐行读 SSE：跳过非 `data:` 行、`[DONE]` 结束；同一帧里 reasoning_content 与 content 都可能有值，按 reasoning → content 顺序回调。非 200 抛 `IllegalStateException("LLM API 返回 <code>: <body>")` |
| `MockLlmClient.java` | 无 key 时的本地假实现，用来先跑通整条流式链路。`CHAR_DELAY_MS = 15`，**按码点切而不是按 char 切**（非 BMP 字符在 UTF-16 占 2 个 char，拆成落单 char 后 Jackson 编不出 UTF-8，SSE 里变成两个 `?`）。思考 / 联网 / 图片各回显一行（「【Mock 思考】」「【Mock 联网】」「【Mock 图片】收到 N 张」），没 key 也能验证这三条链路。取正文一律走 `LlmMessage.text()`（content 是多模态数组时把 text 段拼起来） |
| `LlmContentPart.java` | 多模态 `content` 数组里的一段：`{"type":"text","text":...}` 或 `{"type":"image_url","image_url":{"url":"data:...;base64,..."}}`。`isImage()` **必须 `@JsonIgnore`**：百炼对数组元素做严格校验，多一个它不认识的 `"image": true` 就整请求 400 |

**repository/ — Spring Data JPA**

| 文件 | 方法 |
|---|---|
| `UserRepository.java` | `Optional<User> findByUsername(String)`（登录用，username 有唯一索引）、`List<User> findAllByOrderByIdAsc()`（管理员列表，初始 admin 排第一） |
| `ConversationRepository.java` | `List<Conversation> findAllByOwnerIdOrderByUpdatedAtDesc(Long)`（会话列表，走复合索引）、`Optional<Conversation> findByIdAndOwnerId(Long, Long)`（**唯一的单会话查询入口**，带归属条件）、`int updateTitle(id, ownerId, title)`（**`@Modifying` JPQL 只改 title**：save() 会触发 `@PreUpdate` 刷新 updated_at 把会话顶到列表最前面，而改名不是「活动」；where 带 owner_id，越权改名影响行数为 0） |
| `MessageRepository.java` | `findByConversationIdOrderByIdAsc(Long)`（只留给「不限条数」场景）、`findByConversationId(Long, Pageable)` 与 `findByConversationIdAndIdLessThan(Long, Long, Pageable)`（**id 游标分页**，排序刻意放 Pageable 里而不是方法名上）、`void deleteByConversationId(Long)`。**删除用 `@Modifying` + JPQL 而非派生 `deleteBy`**：派生删除会先 select 再逐条 delete，长会话删一次就是 2N 条 SQL。**调用方需自带事务**（见 `ConversationService.delete` 的 `@Transactional`） |
| `AttachmentRepository.java` | `findLinkableIds(ids, conversationId)`（**只查 id 不查实体**：校验阶段把 LONGBLOB 拉进内存是白费）、`findByMessageIdInOrderByIdAsc(ids)`（拼多模态历史，带字节）、`findMetaByMessageIdIn(ids)`（**JPQL 构造器投影**成 `Object[]{messageId, AttachmentVO}`，消息列表只要元信息）、`findByIdForOwner(id, ownerId)`（读字节前的归属校验）、`linkToMessage(ids, messageId, conversationId)`（`@Modifying`，带 `message_id IS NULL` 条件保证一个附件只挂一条消息）、`deleteByConversationId(id)` |

**service/ — 业务逻辑**

| 文件 | 职责 |
|---|---|
| `ChatService.java` | **全项目最复杂的类**，SSE 流式对话编排。详见第八节 8.1。关键成员：`SSE_TIMEOUT_MARGIN_MS = 30_000`、`DEFAULT_TITLE = "新的对话"`、`TITLE_MAX_LENGTH = 30`、`MAX_THINKING_BUDGET = 262144`（qwen3.8 系最大思维链长度）、`executor = Executors.newVirtualThreadPerTaskExecutor()`、`sseTimeoutMs = requestTimeoutSeconds*1000 + 30000`。`buildOptions()` 统一做模型白名单 + 思考预算校验（思考关着时预算丢弃）；`recentHistory()` 把存库思考随历史回传（preserve_thinking）。内部类 `SurrogateBuffer`、`StreamAbortedException` |
| `ConversationService.java` | 会话 CRUD，**每个公开方法都接 `CurrentUser`**：`create(user)`、`list(user)`、`messages(id, user, before, limit)`（游标分页）、`delete(id, user)`、`requireOwned(id, user)`（404 口径）、`rename(id, user, request)`、`locateRegenerateTarget(id)`（只定位不删除，返回 `DroppedReply(prompt, model, assistantMessageId)`；最后一条不是助手消息就 400）+ `deleteAssistantMessage(id)`（校验都过了再删：模型不支持图片时必须 400 在删除之前，否则用户连旧回答都丢了） |
| `AttachmentService.java` | 图片附件的上传 / 校验 / 挂载 / 读取。`ALLOWED_MIME` 5 种位图（**刻意不含 SVG**：能带脚本）、`MAX_BYTES = 5MB`、`MAX_PER_MESSAGE = 4`。`upload()` 先传后发（上传不等发送）；`validateForConversation()` 一次挡掉「不存在 / 是别人的 / 已被占用」三种情况（都 400）；`requireOwned()` 读字节前查归属（404）。**只依赖 `ConversationService`，不被它反向依赖**，否则构造器循环 |
| `UserService.java` | `login()`、`me()`、`list()`（**不带任何人的 systemPrompt**）、`changePassword()`、`updateSystemPrompt()`（目标 id 只来自 token；全空白存 NULL）。构造时用 `passwordEncoder.encode(UUID.randomUUID())` 预算一个 `dummyHash`：用户名不存在时也拿它做一次 BCrypt 比对，**防时序攻击**（BCrypt 故意做慢，直接返回会让攻击者靠响应快慢筛出存在的用户名）。「用户不存在」与「密码错」返回同一句 401 文案，不给用户名枚举留口子。 |

**resources / sql / test**

| 文件 | 职责 |
|---|---|
| `resources/application.properties` | 主配置，全部可调项写成 `${环境变量:仓库内默认值}`。逐项说明见第五节。 |
| `resources/application-local.properties` | 本地真实密钥（DB 密码 + `llm.api-key`）。**已被 `.gitignore` 忽略**，靠启动参数 `--spring.profiles.active=local` 生效。 |
| `sql/init.sql` | 建库 + 建 4 张表（含 `attachment`）+ `INSERT IGNORE` 种子管理员（`admin` / BCrypt 哈希 / role=1）。全部 `IF NOT EXISTS`，**可重复执行**。结构与 `ddl-auto=update` 的结果一致；「已有库升级」段按日期记录每次表结构迁移的 SQL；末尾备注了两条手工维护项（`Role` 枚举扩值、删会话的删除顺序）。 |
| `src/test/.../ChatbotApplicationTests.java` | 只有一个空的 `contextLoads()`，`@SpringBootTest`。**项目没有其他自动化测试**，需要真 MySQL 才能跑起来。 |


---

## 三、后端架构：分层与调用链

### 3.1 分层规则

```
HTTP 请求
  ↓
AuthInterceptor            （config/WebConfig 注册；先于 @Valid 和控制器执行）
  ↓
controller/                薄层：参数绑定 + 委派，不写业务
  ↓
service/                   业务逻辑 + 事务边界（@Transactional 只出现在这一层）
  ↓
repository/  →  entity/    Spring Data JPA
  ↓
MySQL

service/ChatService  ──→  llm/LlmClient  ──→  外部模型服务（HTTP）
```

约定：

- **依赖方向单向向下**，`controller` 不直接碰 `repository`（`ChatService` 是唯一同时用两个 repository 的 service，为了写消息时顺带刷新 `conversation.updated_at`）。
- **进出网一律用 `dto/` 里的 record**，`entity` 不出控制器。`UserVO` 没有 password 字段就是这个约定的体现。
- **权限判断只在 `AuthInterceptor`**（靠 `@RequireAdmin` 注解），service 层不重复判断。`UserService.list()` 的注释明确写了这一点。
- **业务错误统一抛 `ResponseStatusException(HttpStatus, 中文reason)`**，由 `GlobalExceptionHandler` 转 `ErrorResponse`。项目里没有自定义业务异常类。
- **`CurrentUser` 通过方法形参注入**（`CurrentUserArgumentResolver`），不用 ThreadLocal、不用 `SecurityContextHolder`。

### 3.2 Bean 装配

| Bean | 声明处 | 说明 |
|---|---|---|
| `PasswordEncoder` | `config/AuthConfig` | `BCryptPasswordEncoder()`，strength 10 |
| `LlmClient` | `config/LlmConfig` | 按 `llm.api-key` 是否为空二选一：`MockLlmClient` / `OpenAiCompatibleLlmClient`。**启动时定型，改 key 必须重启** |
| `AuthProperties` / `LlmProperties` | `@EnableConfigurationProperties` | 分别在 `AuthConfig` / `LlmConfig` 上启用 |
| `AuthInterceptor` / `CurrentUserArgumentResolver` | `@Component` | 由 `WebConfig` 注册进 MVC |
| `AdminUserInitializer` | `@Component` + `ApplicationRunner` | 启动完成后跑一次 |
| `ObjectMapper` | Spring Boot 自动配置 | **Jackson 3**，包名 `tools.jackson.databind.ObjectMapper`（不是 `com.fasterxml.jackson.databind`）。`TokenService`、`ChatService`、`OpenAiCompatibleLlmClient` 都注入它 |
| 虚拟线程 Executor | `ChatService` 字段 | `Executors.newVirtualThreadPerTaskExecutor()`，不是 Spring 管理的 Bean，`@PreDestroy` 里自己关 |

### 3.3 三类请求的调用链

**① 登录（唯一免鉴权）**
```
POST /api/auth/login
  → AuthController.login(@Valid LoginRequest)
  → UserService.login()：findByUsername → BCrypt matches（用户不存在则比 dummyHash）
  → TokenService.issue(user) → LoginResponse{token, "Bearer", expiresIn, UserVO}
```

**② 普通 REST（例：拉历史消息）**
```
GET /api/conversations/{id}/messages   [Authorization: Bearer xxx]
  → AuthInterceptor.preHandle：verify → 回表查 user → 比 iat/passwordChangedAt → 存 CurrentUser
  → ConversationController.messages(id)
  → ConversationService.messages(id, user, before, limit)：requireOwned(id, user) 否则 404 → 按 id 游标取一页（多取一条判 hasMore）
  → MessagePageVO{items(时间正序), beforeId, hasMore}
```

**③ SSE 流式对话（唯一异步）**
```
POST /api/conversations/{id}/chat   [Accept: text/event-stream]
  → AuthInterceptor（同上，在请求线程里完成）
  → ChatController.chat → ChatService.chat()
       ├─ 请求线程：requireOwned(conversationId, user) → applyAutoTitle → 用户消息入库 → recentHistory(N)
       ├─ 请求线程：new SseEmitter(sseTimeoutMs) + 注册 onTimeout/onError/onCompletion（都置 cancelled）
       ├─ 请求线程：executor.submit(...) 后【立刻 return emitter】
       └─ 虚拟线程：llmClient.streamChat(history, options, ...) → 逐帧 send(reasoning|delta) → 助手消息入库（含 model + 用量） → send(done 带用量) → complete()
```
> 请求线程与生成线程不是同一个，这是 `CurrentUser` 不能用 ThreadLocal 的原因。

### 3.4 后端编码约定（改代码前请遵守）

1. 注释用中文，**重点写「为什么这么写」而不是「写了什么」**——现有代码里几乎每个非平凡决策都带一段解释性注释，请延续这个风格。
2. DTO / VO / 配置类一律用 `record`；实体类用传统 getter/setter（JPA 需要）。
3. 新增接口**默认受保护**，不需要往 `PUBLIC_PATHS` 加东西；要免登录才改 `WebConfig`。
4. 管理员接口打 `@RequireAdmin`（可打在方法或类上），不要在 service 里手写角色判断。
5. 中文错误文案面向用户，直接写进 `ResponseStatusException` 的 reason。
6. 时间统一 `LocalDateTime`（无时区），比较 token 时才转 `ZoneId.systemDefault()` 的 epoch 毫秒。
7. 改实体 → 必须同步 `chatbot/sql/init.sql`（Hibernate `ddl-auto=update` 只补缺失的表和列，**不会改动已存在的列**）。

---

## 四、前端模块详解

### 4.1 逐文件清单

路径前缀 `chatbot-web/src/`。

| 文件 | 行数级别 | 职责 |
|---|---|---|
| `main.ts` | 5 | `import './assets/main.css'` + `createApp(App).use(router).mount('#app')`。插件只有 vue-router。 |
| `App.vue` | 小 | 根组件只剩两件事：① `router.isReady()` 之前显示「正在恢复登录状态…」；② **对 `isAuthenticated` 的全局 watch**——登录态一旦消失（接口 401、ChatView 的退出登录、账号被禁用）就把界面送回 `/login?next=<当前路径>`。掉登录态的入口不止 401 一条，所以兜底放在这里而不是传输层；也因此 ChatView 不用为路由改任何一行。原来的「三分支权限闸门」已搬进 `router/guards.ts`。 |
| `session.ts` | 小 | `ensureSession()`：本地有 token 时向 `GET /api/auth/me` 做**一次性**有效性确认并缓存 promise（每次导航都进守卫，不能一次导航打一次接口）；成功顺带刷新 localStorage 里的用户信息（角色可能变了）。原来是 `App.vue` 的 onMounted 逻辑，搬进守卫是因为守卫若直接读 localStorage 里的旧角色，「管理员被降级后刷新页面」那一下 `requiresAdmin` 会误判通过。独立成文件是因为 `auth.ts` 不能 import `api.ts`（会与 `api.ts → auth.ts` 成环）。 |
| `router/index.ts` | 小 | `createRouter(createWebHistory())` + 挂守卫 + `scrollBehavior` 归零。**history 模式**：URL 干净，但生产部署必须让静态服务器把未命中路径回退到 `index.html`（nginx `try_files $uri /index.html`），否则直接访问或刷新 `/admin/users` 会拿到服务器 404；开发期 Vite 自带回退，本地永远发现不了这个问题。 |
| `router/routes.ts` | 小 | **模块注册表**，也是「怎么加一个平级功能模块」的答案：`RouteMeta` 类型增强（`moduleId` / `title` / `icon` / `order` / `requiresAdmin` / `hidden` / `public`）+ 全部路由记录。带 `moduleId` 的记录会自动出现在导航条上，带 `requiresAdmin` 的会被守卫自动拦成 `/403`。聊天用静态 import（落地页不能闪空白），管理台用 `() => import()`（普通用户不下载管理台代码）。 |
| `router/guards.ts` | 小 | 登录闸门 + 角色闸门：`beforeEach` 先 `await ensureSession()`，再按 `meta.public` / `isAuthenticated` / `meta.requiresAdmin` 决定放行、跳 `/login?next=` 还是 `/403`；`afterEach` 维护 `document.title`。另导出 `safeNextPath()`：`?next=` 只接受站内绝对路径，挡掉 `//evil.com` 这类协议相对 URL 的开放重定向。 |
| `router/paths.ts` | 小 | `LOGIN_PATH` / `HOME_PATH` / `FORBIDDEN_PATH` 常量。单独一个文件而不是塞进 `routes.ts`：`routes.ts` 要 import 各视图，视图又要这些常量做「回到聊天」链接，放一起就是循环依赖。 |
| `layouts/AppShell.vue` | 小 | 应用外壳：左侧 `ModuleNav` + 内容区 `<RouterView/>`。所有平级模块都作为它的子路由挂在下面，天生共享导航与登录闸门；登录页刻意不在里面（未登录的人不该看到任何模块导航）。`.app-main` 的 `min-width: 0` 不能省：flex 子项默认 `min-width:auto`，聊天区里的长代码块会把导航条挤出视口。 |
| `components/ModuleNav.vue` | 小 | 56px 模块导航条。条目**从路由表派生**（`meta.moduleId` 过滤 + `order` 排序），不在组件里写死，所以加模块不用改它；`requiresAdmin` 的模块对普通用户直接不渲染（真正的拦截在后端 `@RequireAdmin` 和守卫，这里只是不给人看一个必然 403 的按钮）。激活态用 vue-router 自带的 `router-link-active`（含子路由匹配），不用自己算。 |
| `components/icons/IconChat.vue` | 小 | 模块导航图标：24×24 描边、`stroke=currentColor`，颜色由导航条 CSS 决定。新模块照这个规格加一个 SFC，经 `markRaw()` 放进路由 meta（不 markRaw 会被 reactive 代理，白白增加开销）。 |
| `views/ForbiddenView.vue` | 小 | `/403`：已登录但权限不够。显示当前角色 + 「回到聊天」。挂在外壳里，所以还能用导航条去别的模块，不至于困在死页上。 |
| `views/NotFoundView.vue` | 小 | `/:pathMatch(.*)*` 兜底，同样挂在外壳里：登录状态下打错地址还能点导航回去，不用手改 URL。 |
| `auth.ts` | 小 | 登录态。导出 `ROLE_USER=0`/`ROLE_ADMIN=1`（与后端 `Roles` 对齐）、`token`/`currentUser`（模块级 `ref`，初值读 `localStorage` 的 `chatbot.token`/`chatbot.user`）、`isAuthenticated`/`isAdmin`（`computed`）、`setSession(token,user)`、`clearSession()`。`readStoredUser()` 对 JSON 解析失败返回 null（存坏了就当没登录）。**本文件不要 import api.ts**，否则和 `api.ts → auth.ts` 形成循环依赖。 |
| `api/client.ts` | 小 | **全站唯一的 HTTP 传输层**（2026-09-25 从 `api.ts` 抽出）。导出 `API_BASE='/api'`、`request<T>(path, init)`、`withAuth(headers?)`、`extractErrorMessage(response)`。`request()` 的口径：挂 `Authorization: Bearer`、非 2xx 读 `ErrorResponse.message`、**401 一律 `clearSession()`**（界面随即弹回登录页）、204 返回 `undefined`。抽出来的理由：项目要长出一批和聊天**平级的功能模块**，每个模块一个 api 文件互不干扰，但「挂鉴权头 + 401 清登录态」只能有一份实现，否则某个模块自己写 fetch 忘了处理 401，界面就会停在一张点什么都 401 的死页面上。**依赖方向刻意单向 `client.ts → auth.ts`**：本文件不 import router——反向 import 会形成 `router → guards → api → client → router` 的环，ESM 下表现为某个绑定初始化时还是 `undefined`，很难查。「掉登录态就回登录页」的兜底放在 `App.vue`，因为那条路径不止 401 一个入口（还有主动退出登录），兜底要兜在一个口子上。 |
| `api.ts` | 中 | **聊天与登录的接口清单**：只描述「有哪些接口、请求体和响应长什么样」，传输层（fetch 封装 / 鉴权头 / 401 兜底）在 `api/client.ts`。新增别的平级模块请另开 `src/api/<模块>.ts`，不要往这里堆。导出：`login`、`fetchMe`、`changePassword`、`listUsers`、`createConversation`、`listConversations`、`getMessages`（带 `{before, limit}` 分页参数，返回 `MessagePage`）、`deleteConversation`、`renameConversation`、`updateSystemPrompt`、`fetchLlmOptions`、`uploadAttachment(conversationId, file)`（FormData，**不手动设 Content-Type**）、`fetchAttachmentUrl(id)`（**fetch 成 blob 再转 objectURL**：img 标签带不了 Authorization 头）、`streamChat` / `streamRegenerate`（**共用 `consumeSse()` 解析 SSE、`pickStreamOptions()` 拼请求体**，不存在第二份 SSE 契约）、类型 `StreamHandlers` / `StreamOptions` / `MessageUsage` / `MessagePageQuery`。 |
| `types.ts` | 小 | 与后端一一对应：`Conversation`↔`ConversationVO`、`Message`↔`MessageVO`（含 `model` / 三个 token 计数）、`CurrentUser`↔`UserVO`、`LoginResult`↔`LoginResponse`、`ChatStreamEvent`↔`ChatEvent`（`done` 事件带用量）、`MessagePage`↔`MessagePageVO`、`LlmOptions`↔`LlmOptionsVO`（含 `visionModels`）、`Attachment`↔`AttachmentVO`；另有**纯前端**的 `AttachmentRef`（比 `Attachment` 多 `id: number|null` 与本地 `url?`）和 `UiMessage`（比 `Message` 多 `id: number\|null`、`reasoning?`、`error?`、`streaming?`、`model?`、用量三字段） |
| `assets/main.css` | 中 | 全局设计变量（暖中性纸感浅色 / 深墨暗色，跟随系统 `prefers-color-scheme`）+ 半径 / 阴影 / 细滚动条 / 选区 / 焦点环 + **登录与四个弹窗共用的基础件**（`.field*` / `.btn-*` / `.alert-*` / `.modal-*`，放全局是因为长得一样、只写一份） |
| `lib/markdown.ts` | 小 | markdown-it 实例 + 自定义 fence 渲染器（代码块包 `.code-block`、加语言标签和复制按钮）+ `renderMarkdown()`（渲染后过 DOMPurify）。**安全两道锁**：`html:false` 转义输入里的原始 HTML，DOMPurify 再兜一道；`breaks:true` 让单换行也换行。高亮只注册 highlight.js common 子集，认不出的语言原样输出不报错 |
| `views/LoginView.vue` | 中 | 登录表单：两团淡品牌色光晕背景 + 居中卡片（圆角 20、入场动画）。成功后 `setSession()` + `router.replace(safeNextPath(next) ?? HOME_PATH)`——用 replace 而不是 push：登录页不该留在历史记录里，否则登录后按后退又回到登录页。失败展示后端文案并清空密码框。 |
| `views/ChatView.vue` | **大（前端最大文件）** | 聊天主界面：隐形顶栏（会话标题 + `UserMenu` 头像菜单）、居中限宽 760px 的消息列、空状态问候语 + 开场建议 chips、胶囊合成输入框（自动长高、圆形发送/停止、**待发送图片缩略图条 + 粘贴/拖拽传图**）。详见 4.2 |
| `components/ConversationSidebar.vue` | 中 | 纯展示组件。props `conversations`/`activeId`/`canCreate`；emits `select(id)`/`create()`/`remove(id)`/`rename(id, title)`。品牌标 + 虚线「新建对话」；列表项 hover 才浮出重命名/删除图标（盖住时间戳，一行宽度有限）；双击标题或点铅笔行内改名 |
| `components/MessageBubble.vue` | 小 | 单条消息。**助手消息带头像、不套气泡**（长回答铺在背景上比塞进盒子里好读）；用户消息是浅色圆角 pill。图片缩略图排在文字之前（与发给模型的顺序一致）、思考折叠、Markdown 正文、用量行、hover 才显形的「重新生成」 |
| `components/AttachmentThumb.vue` | 小 | 一张图的缩略图。`url` 有值就直接用（本地预览，**不 revoke**——所有权归创建方 ChatView）；只有 `id` 时 `onMounted` 里 `fetchAttachmentUrl()` 取字节转 objectURL、`onBeforeUnmount` 里自己 revoke。点击新标签打开原图；取失败显示「加载失败」而不是空白 |
| `components/ChangePasswordDialog.vue` | 中 | 改密码弹窗，所有人可见。前端先拦一道（新密码 6~64、两次一致），真正校验以后端为准。成功后 `emit('changed', result)` 把新 `LoginResult` 交回父组件（**必须换上新 token，否则下一个请求就 401**），显示成功提示并 1.2 秒后自动关闭；`closeTimer` 在 `onBeforeUnmount` 里清掉。Esc 关闭、点遮罩关闭。 |
| `components/SystemPromptDialog.vue` | 中 | 系统提示词（人设）弹窗，所有人可见。打开时用本地登录态里的 `currentUser.systemPrompt` 回显（不为回显再请求一次 /me）；保存调 `PUT /users/me/system-prompt`，成功后 `emit('changed', user)` 让父组件 `setSession(token, user)` 覆盖本地用户信息——**不用重新登录**。清空保存 = 移除人设 |
| `components/MarkdownContent.vue` | 小 | Markdown 渲染容器：`v-html` 挂 `renderMarkdown()` 的结果；**复制按钮是渲染产物、不在 Vue 事件体系里**，靠容器上的委托监听 + `closest('.copy-btn')` 处理，剪贴板写失败会显示「复制失败」而不是假装成功；`streaming` 为 true 时用 CSS `::after` 在最后一个块末尾挂闪烁光标 |
| `components/UserListDialog.vue` | 中 | 用户管理弹窗，仅 `isAdmin` 时 ChatView 才渲染入口。`onMounted` 调 `listUsers()`，表格列 ID/用户名/角色/创建时间，角色用 `.role-tag`（管理员高亮）+ 灰色 `role=N`。403（普通用户误入）和 401（登录态失效）都落到 `error` 展示。Esc / 点遮罩关闭。 |
| `components/UserMenu.vue` | 小 | 顶栏头像下拉：用户管理（仅 admin）/ 系统提示词 / 修改密码 / 退出登录。点外部或 Esc 关闭。**顶栏只留一个头像按钮**：四个文字按钮并排会把顶栏变成工具条 |

### 4.2 ChatView.vue 的状态与行为

**状态**

| 变量 | 含义 |
|---|---|
| `conversations` | 会话列表（`listConversations()` 的结果） |
| `activeId` | 当前选中会话 id，`null` = 没选中 |
| `messages: UiMessage[]` | 当前会话的消息（**只加载最新一页**，更早的靠「加载更早的消息」往前翻；含正在流式生成的临时项） |
| `input` | 输入框内容 |
| `streaming` | 是否正在生成（禁用输入/思考开关，切换「发送」↔「停止」按钮） |
| `loadingMessages` | 历史消息加载中 |
| `thinkingOn` | 思考模式开关，**默认 `true`**，每条消息显式传给后端。想改用服务端默认值就把它设为 `undefined` 传给 api |
| `llmModels` / `selectedModel` | 模型选择器：清单来自 `GET /api/llm/options`，选中值存 `localStorage.chatbot.model`；存过的模型若已不在白名单（配置改了），回落到服务端默认而不是留一个会 400 的 id |
| `thinkingBudgetSel` | 思考强度档位（`thinking_budget` 的值）：`''`=不限 / 4096 / 16384 / 131072，存 `localStorage.chatbot.thinkingBudget`；思考开关关着时选择器禁用且不下发 |
| `hasMoreOlder` / `olderCursor` / `loadingOlder` | 消息分页三件套：还有没有更早的、往前翻的游标（上一页响应的 `beforeId`）、「加载更早」按钮自己的 loading |
| `PAGE_SIZE` | 常量 50，与后端 `ConversationService.DEFAULT_PAGE_SIZE` 一致（后端上限 200） |
| `fatalError` | 全局错误横幅（列表加载失败、删除失败等） |
| `showPasswordDialog` / `showUserDialog` | 两个弹窗开关 |
| `showPromptDialog` | 系统提示词弹窗开关 |
| `searchOn` | 联网搜索开关，**默认关**（搜索按次计费，不该在用户没注意时 silently 花钱），存 `localStorage.chatbot.search` |
| `visionModels` / `visionEnabled` | 支持图片的模型清单（`GET /api/llm/options` 的 `visionModels`）/ computed「当前选中模型在不在里面」。不在就**隐藏上传按钮**：按钮在却用不了，比没有按钮更让人恼火 |
| `pending: PendingImage[]` | 待发送图片：`{key, id, mime, fileName, size, url?, uploading, error?}`。**选中即上传**（不等点发送），点发送时把 `id` 放进 `attachmentIds` |
| `localUrls: Set<string>` | 本地创建的 objectURL 登记表。URL 在「待发送区 → 已发送气泡」之间是移交的，所以统一在切会话 / 卸载时释放，避免 `AttachmentThumb` 提前 revoke 掉气泡正在用的地址 |
| `activeIsEmpty` | 当前会话是否「一条消息都没有」。**只在历史加载成功后才更新**，加载中/失败保持 false，否则会把「还没读出来」的会话误判成空会话删掉 |
| `abortController` | 当前 SSE 的 `AbortController`（模块级 `let`，非 ref） |
| `scroller` | 消息区 DOM ref，`scrollToBottom()` 用 |
| `canCreateConversation` | computed：`activeId === null \|\| messages.length > 0` |
| `DEFAULT_TITLE` | 常量 `'新的对话'`，**必须与后端 `ChatService.DEFAULT_TITLE` 保持一致** |

**关键行为（都是踩过坑后加的，改之前先读懂）**

| 行为 | 实现 | 为什么 |
|---|---|---|
| 防攒空会话（新建） | `newConversation()`：`canCreateConversation` 为假直接 return；列表里已有标题 == `DEFAULT_TITLE` 的会话就 `selectConversation` 跳过去，不建第二个 | 连点「新建对话」会攒出一排空壳 |
| 防攒空会话（切走） | `selectConversation()` 先记下「要离开的那个是不是空的」（`activeId`/`messages` 一被覆盖就查不到了），**切换成功后**才 `discardEmptyConversation(leavingId)` 静默删掉 | 切换失败时用户还停在原会话上，不能把它删了 |
| 静默删除容错 | `discardEmptyConversation()` 整个 try/catch 吞掉异常 | 最坏情况侧边栏多一个空壳，下次切走还会再试；不能打断用户正在看的对话 |
| 发送 | `send()`：无活动会话就先 `createConversation()`；`input` 清空；`activeIsEmpty = false`；push 用户消息 + push 一个 `{id:null, role:'assistant', content:'', reasoning:'', streaming:true}` 占位；**再从数组里取回响应式代理**（`messages.value[len-1]!`）后改它 | 直接改 push 进去的原始对象不会触发视图更新 |
| 往前翻历史 | `loadOlder()`：拿 `olderCursor` 调 `getMessages(id, {before, limit})`，把返回的 `items` 接到列表**头部**；插入前记下 `scrollHeight` / `scrollTop`，插入后把高度差补回 `scrollTop`；等待期间 `activeId` 变了就把这一页丢掉 | 不补滚动位置的话，往顶部插 50 条会把视口顶下去，用户正在读的那条消息直接跑掉。生成期间禁用：流式增量在往底部追加，补偿会算歪 |
| 流式回调 | `onReasoning` 追加到 `reply.reasoning`、`onDelta` 追加到 `reply.content`、`onDone` 回填 `reply.id` **并当场回填 model / 用量**、`onError` 写 `reply.error`；每次都 `scrollToBottom()`。send 与 regenerate 共用 `streamHandlers(reply)` | — |
| 模型与思考强度 | 输入条两个 `<select>`：模型（`llm.available-models`）+ 思考强度（4096 / 16384 / 131072，对齐百炼 reasoning_effort 的 low / medium 映射，131072 同时是 qwen3.6-flash 的思维链上限）。选择随每条消息下发，存 localStorage | 档位不放进 262144（xhigh）：只有 qwen3.8 系吃得下，qwen3.6-flash 会 400 |
| 人设 | 顶栏「系统提示词」→ 弹窗编辑 → 保存后覆盖本地 `currentUser`，**下一条消息立即生效**（后端每轮把 `CurrentUser.systemPrompt` 拼成 system 消息） | 人设挂在用户上而不是会话上：同一个人所有会话共用一套人设，换账号就是另一套 |
| 顶栏收纳 | 会话标题 + 头像菜单（`UserMenu`）；管理入口全在下拉里 | 聊天界面的顶栏应该几乎隐形，主角是消息列 |
| 空状态 | 问候语 + 三条开场建议 chips，点击填进输入框并聚焦 | 让用户面对空白输入框发呆是最差的开场 |
| 联网搜索 | 输入条「联网」pill，随每条消息下发 `enableSearch`；后端在请求体加 `enable_search: true`（仅 true 时下发）。搜索策略固定默认 turbo | 兼容协议拿不到引用来源，做不了引用 UI；qwen3.8 系在兼容协议下不支持 agent 策略，多一个旋钮只会多一种选错 |
| 传图 | 「图片」按钮（仅 `visionEnabled`）+ 隐藏 `input[type=file]` + textarea 的 `@paste` + 合成框的拖拽。选中即调 `uploadAttachment()`，缩略图条显示「上传中 / 失败」，点 × 移除并 revoke。发送前四道本地校验：张数 ≤4、类型白名单、≤5MB、全部上传成功；带图但模型不支持时横幅拦下，不发请求 | 上传与发送解耦：图片上传可能几百毫秒，攒到点发送那一刻会让按钮明显卡一下。本地校验和后端是同一套规则，后端那道是兜底不是重复 |
| 纯图片提问 | `send()` 允许 `input` 为空、只要 `pending` 非空；发送按钮的 disabled 条件同步改成「没文字且没图」 | 只发图问「这是什么」是正常用法，不该逼用户凑一句废话 |
| 收尾 | `finally` 里 `reply.streaming = false`、`streaming = false`、`abortController = null`、`scrollToBottom()`、`void loadConversations()` | 首条消息会触发后端自动起标题，刷新列表才能拿到新标题和新排序 |
| 停止生成 | `stopStreaming()` = `abortController.abort()`，**不调任何后端接口**。`AbortError` 在 catch 里静默处理 | 后端检测连接断开就停止调模型并把已生成部分入库 |
| 键盘 | `onKeydown()`：Enter 发送、Shift+Enter 换行、**`e.isComposing` 时不发送** | 中文输入法候选态的 Enter 不该触发发送 |
| 启动 | `onMounted`：`loadConversations()` → 有会话就选第一个，没有就 `newConversation()` | — |
| 卸载 | `onBeforeUnmount`：`stopStreaming()` + `clearPending()`（清空待发送图并 revoke 本地 URL） | 退出登录/token 失效被弹回登录页时，必须掐断还在跑的 SSE，否则 fetch 会继续往已不存在的界面写增量，还白烧模型 token；objectURL 不 revoke 会一直占内存 |
| 退出登录 | `logout()` = `stopStreaming()` + `clearSession()` | 顺序不能反 |
| 改密码成功 | `onPasswordChanged(result)` = `setSession(result.token, result.user)` | 后端换发了新 token |
| 删除会话 | `removeConversation(id)`：`window.confirm` 二次确认 → 删 → 若删的是当前会话则清空状态 → 刷列表 → 若没选中项就选第一个 | — |
| 重命名 | 侧边栏双击标题 → 行内输入框 → `PUT /{id}/title` → 只用响应覆盖本地那一条的 title，**不重拉列表** | 重拉列表会搅动滚动位置等状态；后端不刷新 updated_at，所以本地顺序也不用重算 |
| 重新生成 | 最后一条助手气泡下的「重新生成」→ 本地先 `pop()` 掉旧回答、push 流式占位 → `POST /{id}/regenerate` → 后端删库里的旧回答、用同一条用户消息重跑 | 用户消息不重存（它已在库里），历史里自然不含旧回答，所以不会攒出两份回答；中断语义与 send() 完全一致 |

**组件树与数据流**

```
App.vue  （RouterView + 掉登录态回登录页的全局 watch）
├── router/guards.ts   beforeEach：ensureSession() → public? / 未登录 → /login?next= / requiresAdmin → /403
├── LoginView.vue      （顶层路由，不在外壳里）──api.login()──→ setSession() → router.replace(next)
└── layouts/AppShell.vue
    ├── ModuleNav.vue          条目从 routes.ts 的 meta.moduleId 派生
    └── <RouterView/>
        ├── ChatView.vue
        │   ├── ConversationSidebar.vue   props↓ conversations/activeId/canCreate   emits↑ select/create/remove
        │   ├── MessageBubble.vue × N     props↓ message: UiMessage（无 emit，纯展示）
        │   │       └── AttachmentThumb.vue × N   props↓ attachment: AttachmentRef（自己 fetch 字节、自己 revoke）
        │   ├── ChangePasswordDialog.vue  emits↑ close / changed(LoginResult) → ChatView.setSession()
        │   └── UserListDialog.vue        emits↑ close（数据自己拉 listUsers()）
        ├── ForbiddenView.vue  /403
        └── NotFoundView.vue   其余一切路径
```

### 4.3 SSE 流式解析（`api.ts` 的 `streamChat`）

后端 chat 接口是 **POST + SSE**，浏览器原生 `EventSource` 只支持 GET，**用不了**。实现要点：

1. `fetch` + `response.body.getReader()` + `TextDecoder`；**`decoder.decode(value, { stream: true })`**——多字节中文字符可能被切在两个网络包之间，让 decoder 自己缓存半个字符，不能每次 new 一个 decoder。
2. 按 **空行**（`/\r?\n\r?\n/`）切帧；`dispatchEvent()` 取每帧所有 `data:` 行、去前缀（含一个可选空格）、`join('\n')` 后 `JSON.parse`；**解析失败直接 return**（不完整的帧忽略，等下一个事件）。
3. 循环结束后若 buffer 里还剩内容，再 dispatch 一次（服务端关闭连接的边界情况）。
4. `!response.ok || !response.body` 说明**还没升级成 SSE 就失败**（401 未登录 / 404 会话不存在 / 400 参数校验），此时响应体是 JSON 错误体，走 `extractErrorMessage()`，401 同样 `clearSession()`。
5. `enableThinking` 为 `undefined` 时**不往请求体塞这个字段**（而不是塞 null），由服务端配置决定。
6. token 放请求头，**绝不放 URL 查询参数**（会进各种访问日志和浏览器历史）。
7. `signal` 由调用方的 `AbortController` 提供，`abort()` 即「停止生成」。

### 4.4 前端编码约定

1. `<script setup lang="ts">` + Composition API，不用 Options API。
2. 路径一律用别名 `@/`（`vite.config.ts` 里 `@` → `./src`），不写相对路径爬楼。
3. 显式返回类型（`: Promise<void>`、`: string`），`tsconfig.app.json` 开了 `noUncheckedIndexedAccess`，数组下标访问要处理 `undefined`（代码里的 `messages.value[...]!` 就是为此）。
4. 注释用中文，重点写「为什么」，尤其是踩过的坑。
5. 新增页面 / 平级功能模块：在 `views/` 下加组件，在 `router/routes.ts` 里加一条路由记录；带 `meta.moduleId` 自动进导航条，带 `meta.requiresAdmin` 自动被守卫拦权限，**ModuleNav 与 guards 都不用改**。
6. 新增后端调用：聊天 / 登录相关加在 `api.ts`，**别的平级功能模块另开 `src/api/<模块>.ts`**（模块内聚、互不干扰），类型加在 `types.ts`；两者都只能用 `api/client.ts` 的 `request()` / `withAuth()`，**不要在组件里直接写 fetch**，否则丢掉 401 兜底。
7. 登录/弹窗类样式优先复用 `assets/main.css` 里的全局类，组件特有样式才写 `<style scoped>`。


---

## 五、关键配置说明

### 5.1 后端配置（`chatbot/src/main/resources/application.properties`）

敏感项和可调项都写成 `${环境变量:仓库内默认值}` 形式。**真实密钥不要写进会提交的文件**：放 `application-local.properties`（已 gitignore）并加启动参数 `--spring.profiles.active=local`，或用环境变量。

| 配置项 | 仓库内默认值 | 环境变量 | 说明 |
|---|---|---|---|
| `spring.application.name` | `chatbot` | — | |
| `server.port` | `8089` | — | 改了要同步 `chatbot-web/vite.config.ts` 的代理 target |
| `auth.token-secret` | dev 占位值 | `AUTH_TOKEN_SECRET` | **少于 32 字符后端拒绝启动**（`TokenService` 构造函数抛 `IllegalStateException`）。生产必须覆盖 |
| `auth.token-ttl-hours` | `12` | `AUTH_TOKEN_TTL_HOURS` | 过期返回 401，前端自动弹回登录页 |
| `auth.default-admin-username` | `admin` | `AUTH_ADMIN_USERNAME` | 仅 `sys_user` 为空表时由 `AdminUserInitializer` 兜底创建，等价 `init.sql` 的 `INSERT IGNORE` |
| `auth.default-admin-password` | `admin` | `AUTH_ADMIN_PASSWORD` | 同上，入库前做 BCrypt |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/chatbot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` | `DB_URL` | 换库 / 换主机不用改文件 |
| `spring.datasource.username` | `root` | `DB_USERNAME` | 同上 |
| `spring.datasource.password` | `123456` | `DB_PASSWORD` | 只是本地兜底，不是真实密钥 |
| `cors.allowed-origins` | `http://localhost:5173` | `CORS_ALLOWED_ORIGINS` | 跨域白名单，逗号分隔多个。**留空（或全空白）启动直接失败**：CORS 全开只适合内网开发，不该是配置漏写的默认结果 |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | — | |
| `spring.jpa.hibernate.ddl-auto` | `update` | — | 自动建表/补列。**只补缺失的表和列，不改动已存在的列** |
| `spring.jpa.show-sql` | `false` | `SHOW_SQL` | 调试 SQL 时 `SHOW_SQL=true` 启动 |
| `spring.jpa.open-in-view` | `false` | — | 关掉 OSIV，避免懒加载在视图层触发 |
| `llm.base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | — | 任何 OpenAI 兼容接口都能直连（改这里 + `llm.model` 即可换服务商） |
| `llm.api-key` | 空 | `LLM_API_KEY` | **留空自动走 `MockLlmClient`** |
| `llm.model` | `qwen3.6-flash` | — | **只是默认值**：请求体带 `model` 时以请求为准（须落在白名单里） |
| `llm.available-models` | `qwen3.6-flash,qwen3.7-flash,qwen3.8-flash,qwen3.8-max` | `LLM_AVAILABLE_MODELS` | 界面可选模型白名单（逗号分隔）。请求里的 `model` 不在里面就 400 并把清单写进错误文案；加模型改配置即可，不用改代码发版 |
| `llm.enable-thinking` | `true` | `LLM_ENABLE_THINKING` | 映射为请求体顶层 `enable_thinking`（百炼/Qwen 系参数）。**服务商不认识该字段时把这一行整行注释掉**，后端就不下发。取舍见 `chatbot/README.md` |
| `llm.request-timeout-seconds` | `900` | `LLM_REQUEST_TIMEOUT` | **整轮生成的总上限，不是空闲超时**。SSE 超时自动取它 +30 秒 |
| `llm.max-history-messages` | `20` | `LLM_MAX_HISTORY` | 历史的**条数**上限；<=0 不限。与 `llm.max-history-tokens` 谁先满足谁生效 |
| `llm.max-history-tokens` | `24000` | `LLM_MAX_HISTORY_TOKENS` | 历史的 **token 预算**：从最近一条往前累加估算（中文约 1 字 1 token、其余约 4 字符 1 token，思考也计入），超预算截断；<=0 不限。一次最多往前扫 200 条 |
| `llm.vision-models` | `qwen3.6-flash,qwen3.7-flash,qwen3.8-flash,qwen3.8-max` | `LLM_VISION_MODELS` | `available-models` 的子集：能吃图片输入的模型。带图请求打到不在这里的模型直接 400 并在文案里列清单；前端 `GET /api/llm/options` 拿它决定显不显示上传按钮。2026-09-24 实测四个模型在兼容协议下都接受 `image_url` |
| `spring.servlet.multipart.max-file-size` | `5MB` | `UPLOAD_MAX_FILE_SIZE` | 单张图上限，与 `AttachmentService.MAX_BYTES` 同一个数：超了这里给 413（`GlobalExceptionHandler` 接住），controller 里那道给中文 400 文案 |
| `spring.servlet.multipart.max-request-size` | `6MB` | `UPLOAD_MAX_REQUEST_SIZE` | 整个 multipart 请求上限，比单文件留 1MB 余量给边界与表单字段 |

> `LLM_REQUEST_TIMEOUT` 和 `LLM_MAX_HISTORY` 是 properties 里**显式写死**的占位符名，不是 Spring relaxed binding 推出来的 `LLM_REQUEST_TIMEOUT_SECONDS` / `LLM_MAX_HISTORY_MESSAGES`。用错名字不会报错，只会静默走默认值。

`application-local.properties` 当前只有两项（仅本地，不入库）：`spring.datasource.password` 与 `llm.api-key`。**该文件里现在存着一个真实的百炼 API Key**，注意不要复制进任何会提交的文件或对外输出。

### 5.2 前端配置

| 文件 | 内容 |
|---|---|
| `vite.config.ts` | 三件事：插件 `vue()` + `vueDevTools()`；别名 `@` → `./src`；`server.proxy` 把 `/api` 转发到 `http://localhost:8089`（`changeOrigin: true`）。**REST 和 SSE 都走这一条代理**，前端代码里不写死后端地址；后端换端口只改这里 |
| `package.json` | `type: module`；scripts `dev` / `build`(= `run-p type-check build-only`) / `preview` / `build-only` / `type-check`(= `vue-tsc --build`)；运行时依赖 `vue` + `markdown-it` + `highlight.js` + `dompurify`；`engines.node = ^22.18.0 \|\| >=24.12.0` |
| `tsconfig.json` | 只有 `references`，指向 `tsconfig.node.json` 与 `tsconfig.app.json`（`files: []`） |
| `tsconfig.app.json` | 继承 `@vue/tsconfig/tsconfig.dom.json`；`include` = `env.d.ts` + `src/**/*`；**`noUncheckedIndexedAccess: true`**；`paths` = `@/*` → `./src/*`；`tsBuildInfoFile` 指到 `node_modules/.tmp/` |
| `tsconfig.node.json` | 继承 `@tsconfig/node24`；只管 `vite.config.*` 等工具文件；`module: preserve`、`moduleResolution: bundler`、`types: [node]`、`noEmit: true` |
| `env.d.ts` | 一行 vite/client 类型引用 |
| `index.html` | `lang="zh-CN"`，标题 `Chatbot`，挂载点 `#app`，入口 `/src/main.ts` |

`.vue` 的类型信息由 `vue-tsc` 提供（`tsc` 认不出来），编辑器侧需要装 Vue (Official) 扩展。

### 5.3 构建与版本控制

| 文件 | 内容 |
|---|---|
| `.gitignore`（根） | 一份覆盖两个子项目。重点条目：`node_modules/`、`dist/`、`target/`、`build/`、`.idea/`、`*.iml`、`.vscode/*`（保留 `extensions.json`）、Eclipse/NetBeans 产物、`chatbot/.mvn/wrapper/maven-wrapper.jar`、`*.log`、`.eslintcache`、`__screenshots__/`、`HELP.md`、`*.local`、**`application-local.properties`**（第 72 行）、`.env`、**`AGENTS.md`（第 75 行）** |
| `chatbot/.gitattributes` | `/mvnw text eol=lf`、`*.cmd text eol=crlf`——保证 Maven Wrapper 脚本在 Windows 上换行符正确 |
| `chatbot-web/.vscode/` | `extensions.json`（入库）+ `settings.json`（gitignore） |
| `chatbot/target/` | 后端构建产物，存在于工作区但已 gitignore。**检索代码时请排除 `target/`、`node_modules/`、`dist/`、`.idea/`** |

---

## 六、数据库设计

### 6.1 表结构

**`sys_user`（用户）** — 实体 `entity/User.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | |
| `username` | `varchar(50)` | NOT NULL, UNIQUE `uk_sys_user_username` | 业务上按「忽略首尾空格」后的值存取（`login()` 里 `.trim()`） |
| `password` | `varchar(100)` | NOT NULL | BCrypt 哈希（固定 60 字符，留余量换算法）。**任何接口都不返回该字段** |
| `role` | `int` | NOT NULL | `0`=普通用户，`1`=管理员。常量在 `auth/Roles.java` |
| `created_at` | `datetime(6)` | NOT NULL | `@PrePersist` 写入，`updatable=false` |
| `password_changed_at` | `datetime(6)` | NULL | 从没改过密码为 NULL。签发时间（token `iat`）早于它的登录态一律作废 → **改密码会踢掉其他所有设备的会话**。**这列必须保持 `datetime(6)`**：后端按毫秒比较，精度掉到秒会让改密码那一秒签发的旧 token 躲过失效判断 |
| `system_prompt` | `text` | NULL | 该用户的系统提示词（人设）；NULL = 没设，后端不下发 system 消息。2026-09-24 加入 |

**`conversation`（会话）** — 实体 `entity/Conversation.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | |
| `title` | `varchar(100)` | NOT NULL | 创建时写死「新的对话」；首条消息后 `ChatService.applyAutoTitle()` 自动改成消息前 30 字（超出加 `…`） |
| `owner_id` | `bigint` | NOT NULL, FK `fk_conversation_owner` → `sys_user(id)`；复合索引 `idx_conversation_owner_updated(owner_id, updated_at)` | 会话归属用户。列表 / 读取 / 删除 / 发消息全部带它过滤；**查不到（不存在或属于别人）一律 404**，不返回 403 |
| `created_at` | `datetime(6)` | NOT NULL | |
| `updated_at` | `datetime(6)` | NOT NULL | **会话列表按它倒序**。`@PreUpdate` 自动刷新 + `ChatService.saveMessage()` 手动 set |

> `owner_id` 是 2026-09-24 加的（之前会话全站共享，是当时最大的功能缺口，见第十四节变更记录）。**管理员也没有跨用户特权**：`GET /api/users` 是管理员接口，但会话接口对管理员同样只返回他自己的。
> 复合索引由实体上的 `@Index` 声明，Hibernate 建表时就会建；旧文档里「数据量大后自行加 `updated_at` 索引」的备注已不需要。

**`message`（消息）** — 实体 `entity/Message.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | 历史消息按它升序取 |
| `conversation_id` | `bigint` | NOT NULL, FK → `conversation(id)`，索引名 `FK6yskk3hxw5sklwgi25y6d5u1l` | `@ManyToOne` LAZY |
| `role` | `enum('ASSISTANT','USER')` | NOT NULL | `@Enumerated(EnumType.STRING)`，Hibernate 按**字母序**生成枚举值 |
| `content` | `longtext` | NOT NULL | `columnDefinition = "LONGTEXT"` |
| `reasoning` | `longtext` | NULL | 思考过程全文；没开启思考或非推理模型时为 NULL。2026-09-24 加入（见「已有库升级」第二段） |
| `model` | `varchar(64)` | NULL | 生成本条回答实际用的模型 id；用户消息为 NULL |
| `prompt_tokens` | `int` | NULL | 输入 token（含历史上下文）；流式用量拿不到就保持 NULL |
| `completion_tokens` | `int` | NULL | 输出 token（思考 + 回答） |
| `reasoning_tokens` | `int` | NULL | 其中思考占的输出 token；服务商没给 details 时 NULL |
| `created_at` | `datetime(6)` | NOT NULL | |

> 思考过程（reasoning）**随助手消息一起入库**（`message.reasoning`，可空 LONGTEXT）：刷新页面后折叠块还能展开重读。正文 `content` 仍然只含 `delta` 拼出来的正式回答，思考不会混进正文、也不会进送给模型的历史。空白思考存 NULL 而不是空串——前端靠「有没有值」决定要不要渲染折叠块。

**`attachment`（图片附件）** — 实体 `entity/Attachment.java`，2026-09-24 加入

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | 上传接口的返回值，发消息时放进 `ChatRequest.attachmentIds` |
| `conversation_id` | `bigint` | NOT NULL, FK `fk_attachment_conversation` → `conversation(id)`，索引 `idx_attachment_conversation` | **RESTRICT**：删会话前必须先删附件（`ConversationService.delete` 的顺序是 附件 → 消息 → 会话） |
| `message_id` | `bigint` | NULL，索引 `idx_attachment_message` | 关联到的**用户消息** id。**刻意不做外键也不做 `@ManyToOne`**：附件先于消息存在（先上传拿 id、再发消息），可空关联绕不出这个先后顺序；NULL = 传了还没发出去的孤儿 |
| `mime` | `varchar(64)` | NOT NULL | 只能是 `AttachmentService.ALLOWED_MIME` 里的 5 种位图（png/jpeg/webp/gif/bmp，**无 SVG**）；出网时直接当 `Content-Type` |
| `file_name` | `varchar(255)` | NOT NULL | 原始文件名，只用于展示；入库前去掉路径分隔符 |
| `size_bytes` | `bigint` | NOT NULL | 原始字节数。**列名带 `_bytes`**：JPQL 里 `size` 是保留函数名 |
| `data` | `longblob` | NOT NULL | 图片字节。单机部署少一个「文件跑哪去了」的运维面；要多实例就把它换成对象存储 key |
| `created_at` | `datetime(6)` | NOT NULL | |

> **一个附件只属于一条消息**：`linkToMessage` 的 UPDATE 带 `message_id IS NULL` 条件，重复提交同一个 id 会 400「附件不可用」。这条不变式让「重跑历史」时图片不会重复带。
> **字节存库而不是存磁盘**是刻意取舍：备份变慢、库变大，换来部署面只有一个 MySQL。限 5MB × 4 张/消息把量级压住。
> **孤儿附件没有定时清理**：传了没发的附件会一直留在库里，直到它所属的会话被删（前端「空会话回收」会顺手带走）。见 13.1。

### 6.2 初始化与结构同步

两条等价路径，任选其一：

1. 执行 `chatbot/sql/init.sql`：建库（`utf8mb4` / `utf8mb4_unicode_ci`）+ 建 3 张表 + `INSERT IGNORE` 种子管理员 `admin`（`admin` 的 BCrypt cost=10 哈希，role=1）。全部 `IF NOT EXISTS` / `INSERT IGNORE`，**脚本可重复执行**，也不会把改过的密码覆盖回 `admin`。
2. 直接启动后端：`ddl-auto=update` 自动建表，`AdminUserInitializer` 在 `sys_user` 为空时自动建默认管理员。

**同步规则**：表结构的唯一事实源是 `entity/` 下的三个实体类。改实体后必须同步 `sql/init.sql`——`ddl-auto=update` 只补新表新列，**不会修改或删除已有列**，两边不一致时脚本会悄悄过期。 另外 `init.sql` 末尾有一段「已有库升级」注释（2026-09-24 会话归属那次留下的迁移 SQL）：给**已经有数据的旧库**手工执行 ALTER 用。以后再做破坏性结构变更，请往那段下面追加，别只改 CREATE TABLE——`IF NOT EXISTS` 对旧库是空操作。


---

## 七、API 接口汇总

### 7.1 全量接口表（共 15 个）

| # | 方法 | 路径 | 鉴权 | 成功状态码 | 请求体 | 响应体 | 后端入口 |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | **无（唯一白名单）** | 200 | `LoginRequest` | `LoginResponse` | `AuthController.login` |
| 2 | GET | `/api/auth/me` | 需要 | 200 | — | `UserVO` | `AuthController.me` |
| 3 | POST | `/api/conversations` | 需要 | **201** | — | `ConversationVO` | `ConversationController.create` |
| 4 | GET | `/api/conversations` | 需要 | 200 | — | `ConversationVO[]`（`updated_at` 倒序） | `ConversationController.list` |
| 5 | GET | `/api/conversations/{id}/messages` | 需要 | 200 | —（query：`before` 游标、`limit` 单页条数，默认 50 / 上限 200） | `MessagePageVO`（items 按 id 升序 + `beforeId` + `hasMore`） | `ConversationController.messages` |
| 6 | DELETE | `/api/conversations/{id}` | 需要 | **204**（无响应体） | — | — | `ConversationController.delete` |
| 7 | POST | `/api/conversations/{id}/chat` | 需要 | 200 + `text/event-stream` | `ChatRequest`（含可选 `model` / `thinkingBudget`） | SSE 事件流 | `ChatController.chat` |
| 8 | PUT | `/api/users/me/password` | 需要 | 200 | `ChangePasswordRequest` | `LoginResponse`（**新 token**） | `UserController.changePassword` |
| 9 | GET | `/api/users` | **`@RequireAdmin`** | 200 | — | `UserVO[]`（按 `id` 升序） | `UserController.list` |
| 10 | PUT | `/api/users/me/system-prompt` | 需要 | 200 | `UpdateSystemPromptRequest` | `UserVO`（更新后，含自己的 systemPrompt） | `UserController.updateSystemPrompt` |
| 11 | PUT | `/api/conversations/{id}/title` | 需要 | 200 | `RenameConversationRequest` | `ConversationVO`（更新后的那一条） | `ConversationController.rename` |
| 12 | POST | `/api/conversations/{id}/regenerate` | 需要 | 200 + `text/event-stream` | `RegenerateRequest`（**整个体可省略**；`model` 不传沿用旧回答的模型） | SSE 事件流（与 7 号接口同一套） | `ChatController.regenerate` |
| 13 | GET | `/api/llm/options` | 需要 | 200 | — | `LlmOptionsVO`（可选模型清单 + 服务端默认模型 + **visionModels**） | `LlmController.options` |
| 14 | POST | `/api/conversations/{id}/attachments` | 需要 | **201** | multipart，字段名 `file`（单张 ≤5MB、MIME 白名单 5 种、每会话可多次传） | `AttachmentVO` | `ConversationController.uploadAttachment` |
| 15 | GET | `/api/attachments/{id}` | 需要 | 200 | — | 图片字节（`Content-Type` = 存的 mime，`nosniff`） | `AttachmentController.get` |


> 3~7 号接口的控制器方法都声明了 `CurrentUser` 形参（解析见 `CurrentUserArgumentResolver`），归属校验统一在 `ConversationService` / `ChatService` 里做：**查不到或不是自己的会话一律 404**，管理员也没有跨用户特权。
前端封装位置：全部在 `chatbot-web/src/api.ts`，一一对应 `login` / `fetchMe` / `createConversation` / `listConversations` / `getMessages(id, {before, limit})` / `deleteConversation` / `streamChat` / `changePassword` / `listUsers` / `uploadAttachment` / `fetchAttachmentUrl`。
> 14 号超大小限制返回 **413**（不是 400）：异常在 multipart 解析阶段抛出，到不了 `AttachmentService` 里那道给中文文案的校验，所以由 `GlobalExceptionHandler` 的 `MaxUploadSizeExceededException` 处理器接住。15 号**不能用 `<img src>` 直接引用**：img 带不了 `Authorization` 头，而项目刻意不做 `?token=` 兜底。

### 7.2 数据形状

```jsonc
// LoginRequest
{ "username": "admin", "password": "admin" }

// LoginResponse（登录、改密码共用）
{
  "token": "<base64url(payload)>.<base64url(HMAC-SHA256)>",
  "tokenType": "Bearer",
  "expiresIn": 43200,          // 秒，= auth.token-ttl-hours * 3600
  "user": {
    "id": 1, "username": "admin", "role": 1,
    "roleLabel": "管理员",     // 后端给中文名，前端不维护映射
    "createdAt": "2026-09-08T10:00:00"
  }
}

// UserVO —— 没有 password 字段；systemPrompt 只在「自己」的响应里出现，管理员的用户列表里恒为 null
{ "id": 1, "username": "admin", "role": 1, "roleLabel": "管理员", "systemPrompt": "你是资深 DBA…", "createdAt": "..." }

// ConversationVO
{ "id": 12, "title": "新的对话", "createdAt": "...", "updatedAt": "..." }

// MessageVO —— role 是小写字符串；attachments 只有用户消息会有，没图时整个字段不下发
{ "id": 34, "role": "user", "content": "你好", "createdAt": "..." }
{ "id": 40, "role": "user", "content": "这张图里有什么？", "attachments": [ { "id": 9, "mime": "image/png", "fileName": "dashboard.png", "size": 12427 } ], "createdAt": "..." }
{ "id": 35, "role": "assistant", "content": "…", "reasoning": "…", "model": "qwen3.8-flash", "promptTokens": 4, "completionTokens": 30, "reasoningTokens": 12, "createdAt": "..." }


// MessagePageVO —— GET /api/conversations/{id}/messages 的响应（分页）
{ "items": [ { "id": 34, "role": "user", "content": "你好", "createdAt": "..." } ], "beforeId": 34, "hasMore": true }
// items 已按时间正序；beforeId=null 表示没有更早的了；hasMore=false 时前端不再显示「加载更早的消息」
// ChatRequest
{ "message": "你好", "enableThinking": true, "model": "qwen3.8-max", "thinkingBudget": 16384, "enableSearch": true, "attachmentIds": [9] }
// enableThinking 可省略（= 用服务端 llm.enable-thinking）；model 可省略（= 用 llm.model，须落在 llm.available-models 里）；
// thinkingBudget 可省略（= 不下发 thinking_budget），思考关着时后端忽略它；
// attachmentIds 可省略（= 纯文本）；非空时 model 必须落在 llm.vision-models 里，否则 400；message 与 attachmentIds 不能同时为空

// AttachmentVO —— POST /api/conversations/{id}/attachments 的响应（201）
{ "id": 9, "mime": "image/png", "fileName": "dashboard.png", "size": 12427 }

// RegenerateRequest —— 整个体可省略
{ "enableThinking": true, "model": "qwen3.8-max", "thinkingBudget": 16384 }

// LlmOptionsVO —— GET /api/llm/options
{ "models": ["qwen3.6-flash", "qwen3.7-flash", "qwen3.8-flash", "qwen3.8-max"], "defaultModel": "qwen3.6-flash",
  "visionModels": ["qwen3.6-flash", "qwen3.7-flash", "qwen3.8-flash", "qwen3.8-max"] }

// ChangePasswordRequest
{ "oldPassword": "admin", "newPassword": "new-secret" }

// RenameConversationRequest —— title 会被 trim；1~100 字，超了 400
{ "title": "周会纪要" }

// UpdateSystemPromptRequest —— 全空白 = 清除人设；2000 字上限
{ "systemPrompt": "你是资深 DBA，只回答数据库问题" }

// RegenerateRequest —— 整个请求体都可以省略
{ "enableThinking": false }

// ErrorResponse（所有非 SSE 接口的错误体）
{
  "timestamp": "2026-09-21T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "会话不存在: 3",
  "path": "/api/conversations/3/messages"
}
```

**状态码语义**

| 码 | 触发场景 |
|---|---|
| 400 | `@Valid` 校验失败（新密码长度不符）、请求体不是合法 JSON、原密码不正确、新密码与原密码相同、**文本和图片同时为空**、附件 id 不可用（不存在 / 属于别的会话 / 已被别的消息占用）、带图但模型不在 `llm.vision-models` 里、上传 MIME 不在白名单、单张超 5MB |
| 413 | 上传超过 `spring.servlet.multipart.max-file-size`（multipart 解析阶段就抛，`GlobalExceptionHandler` 接住给中文文案） |
| 401 | 未带 token、token 格式错/签名错/已过期、账号已被删、改过密码导致旧 token 失效。**前端收到任何 401 都 `clearSession()` 弹回登录页** |
| 403 | 普通用户访问 `@RequireAdmin` 接口（`GET /api/users`） |
| 404 | 会话不存在、**会话属于别人**（归属校验刻意不返回 403，免得泄露「这个 id 存在」）、用户不存在、**附件不存在或属于别人的会话**（同一口径） |
| 204 | 删除会话成功 |

### 7.3 SSE 事件契约（`POST /api/conversations/{id}/chat`）

由 `dto/ChatEvent.java` 产出，`@JsonInclude(NON_NULL)` 让 null 字段不出现在 JSON 里。每帧形如 `data:{...}` + 空行。

| `type` | 形状 | 语义 |
|---|---|---|
| `reasoning` | `{"type":"reasoning","content":"..."}` | 思考过程增量，出现在正式回答之前。仅推理模型且思考开关为 true 时出现。流式期间推给前端，生成结束后**随助手消息入库**（`message.reasoning`） |
| `delta` | `{"type":"delta","content":"..."}` | 正式回答的增量文本，逐段推送。前端累加即为完整回答，也是唯一入库的内容 |
| `done` | `{"type":"done","messageId":123,"model":"qwen3.8-max","prompt_tokens":4,"completion_tokens":30,"reasoning_tokens":12}` | 生成结束，助手消息已入库。除 `messageId` 外**附带 model 与用量**（下划线命名、NON_NULL）：前端流式期间只有本地占位消息，不回填用量行要等刷新才出现；老前端只读 messageId 不受影响 |
| `error` | `{"type":"error","content":"..."}` | 出错。**文案在 `content`，不是 `message` 字段** |

约定：前端遇到不认识的 `type` **直接忽略**（`api.ts` 的 `switch` 没有 default 分支），保持向后兼容；新增事件类型时老前端不会崩。

为什么必须转发思考、停止生成的语义、SSE 超时如何推导，见 `chatbot/README.md`。

`POST /{id}/regenerate` **复用同一套事件**：前端 `streamRegenerate` 与 `streamChat` 共用 `consumeSse()` 解析，不存在第二份 SSE 契约。

### 7.4 手动测试

`chatbot/README.md` 的「手动测试」一节有 4 条 `curl.exe` 命令（登录换 token → 建会话 → SSE 对话 → 404/400 错误体）。注意：**拦截器跑在 `@Valid` 和控制器之前**，所以不带 token 时四条全部返回 401，想看到 404 和 400 必须先带 token。

---

## 八、核心流程

### 8.1 聊天流程（`ChatService`）

> 带图片的消息走同一条链路，差别只有两处：`recentHistory` 会把该消息的附件字节 base64 内联成 `image_url` 段；`regenerate` 在删除旧回答**之前**先按「删除前的历史」查一次视觉支持（助手消息从不带图，所以两遍看到的图是同一批），免得模型不支持图片时旧回答白删。

```
【请求线程】
POST /api/conversations/{id}/chat  {message, enableThinking?, attachmentIds?}
  → AuthInterceptor 校验通过
  → ChatService.chat(conversationId, request, user)
      1. conversationService.requireOwned(id, user) // 不存在或不是自己的都 404，且必须在提交异步任务之前
      2. attachmentService.validateForConversation  // 附件 id 不存在 / 是别人的 / 已被占用 → 400（在落库之前，不留半条消息）
         文本与附件同时为空 → 400「message 不能为空」
      3. buildOptions(...) + 带图时 requireVisionModel  // 模型白名单 + 视觉白名单，都在落库之前
      4. applyAutoTitle(conversation, text, hasImages)  // 标题仍为「新的对话」时取消息前 30 字；纯图片提问给「[图片]」
      5. saveMessage(conversation, USER, text) + linkToMessage(ids, messageId)  // 附件挂到这条用户消息上
      6. recentHistory(id, user.systemPrompt)       // 从新往旧累加估算 token：条数上限与 token 预算谁先满足谁生效
                                                    //   思考也计入预算（preserve_thinking 会回传）；至少保留最新一条
                                                    //   人设非空时作为 system 消息放最前面，不占历史名额
                                                    //   带附件的消息在这里变成多模态 content 数组（图在前、文在后）
      7. requireVisionSupport(history, model)       // 历史窗口里有旧图而模型不支持 → 400（消息已落库，换模型重生成即可恢复）
      8. new SseEmitter(sseTimeoutMs)               // = requestTimeoutSeconds*1000 + 30000
         onTimeout / onError / onCompletion 都只做一件事：cancelled.set(true)
      9. executor.submit(() -> stream(...))         // 虚拟线程
     10. return emitter                             // 请求线程到此结束

【虚拟线程】stream()
  两个 SurrogateBuffer（reasoning / content 各一个）+ 一个 StringBuilder full + AtomicLong reasoningChars
  try {
    llmClient.streamChat(history, enableThinking, listener)
      listener.onReasoning(t): checkCancelled → reasoningChars += len → buffer.feed(t) → send(reasoning)
      listener.onToken(t):     checkCancelled → full.append(t)     → buffer.feed(t) → send(delta)
    saveMessage(conversation, ASSISTANT, full, reasoningFull)  // 回答进 content、思考进 reasoning
    send(done(saved)); emitter.complete()   // done 带 model + 用量，前端当场回填
  } catch (StreamAbortedException) {                // 用户停止 / 关页面 / SSE 超时
    savePartial(full); completeQuietly()            // 保留已生成内容，不再往前端写任何东西
  } catch (Exception e) {
    log.warn; savePartial(full);
    sendQuietly(error(describe(e))); completeQuietly()
    // 故意不用 completeWithError(e)：那会让 Spring 把异常抛回已提交的 text/event-stream，
    // 日志里多一条没意义的 IllegalStateException
  }
```

**取消语义**：前端 abort（点停止 / 关页面 / 切会话 / 退出登录 / 组件卸载）、SSE 超时、客户端断网，都会置 `cancelled`。正在跑的生成在**下一个增量**立刻停下——`onReasoning` 和 `onToken` 里都检查，只在回答流里检查的话，思考阶段点「停止」要等到回答开始才生效。`send()` 失败也转成 `StreamAbortedException`。

**SurrogateBuffer 存在的理由**：emoji 等非 BMP 字符在 UTF-16 里占 2 个 char，正好被切成两段增量时，单个 char 编不出 UTF-8，Jackson 会写成 `?`，前端看到乱码。`feed()` 检测到末尾是高位代理字符就把它扣下来留到下一次。流结束时若仍有扣留字符，只记一条 debug 日志并跳过推送。

**重新生成（`POST /{id}/regenerate`，SSE 契约与 /chat 完全一致）**

```
→ ChatService.regenerate(conversationId, request, user)
    1. conversationService.requireOwned(id, user)        // 与 chat 同一个归属口径，404
    2. conversationService.dropLastAssistantMessage(id)  // 删最后一条助手消息，返回它前面那条用户消息正文
                                                         //   最后一条不是助手消息（或空会话）→ 400
    3. recentHistory(id)                                 // 旧回答已不在历史里
    4. startStream(...)                                  // 与 chat 共用 SSE 骨架；用户消息不重存
```

> 重新生成**只能重跑最后一条回答**：按钮只挂在最后一条助手气泡上。要改更早的轮次，语义上等于「从那里重开一条分支」，那是另一个功能，别在这个接口上叠。

### 8.2 鉴权流程

```
登录：POST /api/auth/login
  → UserService.login(): findByUsername(username.trim())
       用户不存在 → 拿预计算的 dummyHash 做一次 BCrypt 比对（防时序攻击）
       matches 失败 → 401「用户名或密码错误」（不区分账号不存在还是密码错）
  → TokenService.issue(user): payload = {uid, username, role, iat(ms), exp(ms)}
       token = base64url(payloadJson) + "." + base64url(HMAC-SHA256)
  → LoginResponse{token, "Bearer", ttlSeconds, UserVO}

后续请求：Authorization: Bearer <token>
  → AuthInterceptor.preHandle()
       ① handler 不是 HandlerMethod → 放行（CORS 预检 / 静态资源 / 404）
       ② resolveToken()：只认 Bearer 头，不做 ?token= 兜底
       ③ TokenService.verify()：切分 → base64url 解码 → MessageDigest.isEqual 常量时间比签名 → 反序列化 → 比 exp
       ④ userRepository.findById(payload.uid())：账号没了 → 401
       ⑤ payload.iat() < user.passwordChangedAt(转 epoch ms) → 401「密码已修改，请重新登录」
       ⑥ new CurrentUser(id, username, role)；@RequireAdmin 且非管理员 → 403
       ⑦ request.setAttribute("chatbot.currentUser", currentUser)
  → CurrentUserArgumentResolver 把它注入控制器形参
```

### 8.3 改密码流程（为什么当前设备不掉线、其他设备掉线）

```
PUT /api/users/me/password  {oldPassword, newPassword}
  → UserService.changePassword(currentUser, request)   // 目标用户 id 只来自 token，不接受请求体传 userId
       ① 原密码 BCrypt 不匹配 → 400「原密码不正确」
       ② 新密码与原密码相同   → 400「新密码不能与原密码相同」
       ③ 写入新哈希 + passwordChangedAt = now()
       ④ issueSession(user) → 换发一个 iat = now() 的新 token
  → 前端 ChangePasswordDialog emit('changed') → ChatView.onPasswordChanged → setSession(newToken, newUser)

结果：新 token 的 iat >= passwordChangedAt，继续有效；
      其他设备上旧 token 的 iat < passwordChangedAt，下一个请求即 401 被踢回登录页。
      iat 用【毫秒】而不是秒，否则「改密码那一秒内签发的旧 token」会躲过失效判断。
```

### 8.4 前端启动与权限闸门

```
浏览器加载 → main.ts → app.use(router) → 首次导航进守卫
  guards.beforeEach(to):
    await ensureSession()          // 本地有 token 才发 GET /api/auth/me，且整个页面加载只发一次
      200 → setSession(token, me)（顺带刷新用户信息，角色可能变了）
      失败 → api/client.ts 已 clearSession()，这里再兜一次
    to.meta.public 且已登录且是 /login → 重定向 /chat
    未登录 → /login?next=<to.fullPath>
    to.meta.requiresAdmin 且 !isAdmin → /403
  App.vue：router.isReady() 之前显示「正在恢复登录状态…」，之后渲染 <RouterView/>
  ChatView.onMounted → loadConversations() → 有会话选第一个，没有就 newConversation()
```

运行期任何接口返回 401 → `api/client.ts` 的 `clearSession()` → `isAuthenticated` 变 false →
**App.vue 里对 `isAuthenticated` 的全局 watch** 把界面送回 `/login?next=<当前路径>`。
掉登录态的入口不止 401 一条（还有 ChatView 的「退出登录」直接调 `clearSession()`），
所以兜底放在这个 watch 里而不是放在传输层：一个口子管所有路径，ChatView 也因此不用为路由改任何一行。
「直接输 URL 绕过登录」则由守卫保证不存在——闸门从 App.vue 的条件渲染搬到了 `beforeEach` 里。

### 8.5 空会话回收

```
newConversation():
  canCreateConversation 为假（当前会话还没消息）→ 直接 return，不建新会话
  列表里已有 title == "新的对话" 的会话 → selectConversation 跳过去，不建第二个
  否则 createConversation() → loadConversations() → activeId = null（强制重拉）→ selectConversation(newId)

selectConversation(id):
  记下 leavingId / leavingWasEmpty → 切换 → resetPaging() → 拉**最新一页**（limit=50）→ 成功后若 leavingWasEmpty 则 discardEmptyConversation(leavingId)

send():
  activeId 为 null（比如刚删完）→ 先 createConversation()
  activeIsEmpty = false（这条消息一发出去它就不是空会话了，切走时不该被删）
```

---

## 九、安全设计要点

1. **密码存储**：BCrypt（cost 10）哈希，从不存明文，任何接口都不返回该字段（`UserVO` 里没有 password）。
2. **防用户名枚举**：登录时对「用户不存在」和「密码错」返回同一句 401 文案。
3. **防时序攻击**：`UserService` 构造时预算 `dummyHash`，用户不存在时也走一次 BCrypt 比对，响应耗时一致。
4. **登录请求体不做格式校验**：`LoginRequest` 只有 `@NotBlank`，不给爆破者回显格式信息。
5. **密码策略**：新密码 6~64 且不能与旧密码相同；`oldPassword` **不设长度下限**——种子管理员密码 `admin` 只有 5 位，加了下限就没人能改密码了。
6. **越权防护**：改密码的目标用户 id 只来自 token（`CurrentUser`），**不接受请求体传 userId**。
7. **Token 格式**：无状态自签 `base64url(payloadJson).base64url(HMAC-SHA256)`；密钥短于 32 字符后端拒绝启动；签名用 `MessageDigest.isEqual` 常量时间比较，避免靠响应耗时逐字节猜签名。
8. **改密码即失效**：`iat` 用 epoch **毫秒**，`AuthInterceptor` 比对 `iat` 与 `password_changed_at`（同样转毫秒），早于后者的 token 立即失效。
9. **每请求回表查用户**：无状态 token 本可以做到不查库，但这里刻意查一次，让删号、改角色、改密码能立刻生效。代价是每个请求多一次主键查询。
10. **白名单鉴权**：`/api/**` 默认全部要登录，`PUBLIC_PATHS` 只有登录接口一项。新加接口默认受保护，不用记着往白名单补一行——白名单越长越容易漏，漏一个就是裸奔的接口。
11. **权限注解**：管理员接口打 `@RequireAdmin`，检查统一在 `AuthInterceptor`，普通用户拿 403。
12. **token 不上 URL**：只认 `Authorization: Bearer`，不做 `?token=` 兜底，免得 token 落进访问日志和浏览器历史。SSE 也因此必须用 `fetch` 而非 `EventSource`（后者不支持自定义头）。
13. **密钥不入库**：真实 DB 密码与 LLM API Key 放 `application-local.properties`（gitignore）或环境变量。
14. **日志不打密码**：`AdminUserInitializer` 创建初始管理员时故意不打印密码，因为日志会被收集和转发。

15. **会话归属校验**：会话接口的控制器都接 `CurrentUser`，service 层按 `(id, owner_id)` 查；不存在**或属于别人**一律 404 而不是 403——403 会把「这个 id 确实存在」泄露出去，而 id 是自增的，等于让人枚举出全站有多少会话。管理员**没有**跨用户查看 / 删除的特权，真要做跨用户管理得单独设计（审计、转交、级联删），别指望在现有接口上加个角色判断就完事。

16. **CORS 白名单来自配置**：`cors.allowed-origins` 默认只有 Vite 的 5173，对外部署用 `CORS_ALLOWED_ORIGINS` 覆盖；留空启动失败而不是退化成 `*`。非白名单源的跨域请求拿 403 且不带 `Access-Control-Allow-Origin` 头，同源请求和服务器间调用（不带 Origin 头）不受影响。
17. **人设隐私**：`system_prompt` 只在用户自己的 `/me`、登录响应和改人设响应里出现；管理员 `GET /api/users` 恒为 null——「能列用户」不等于「能看别人的设置」。改人设**不换发 token**（与改密码刻意不同）：它不是安全事件，不该踢掉自己的其他设备。 |
---
18. **附件字节走鉴权接口而不是公开 URL**：`GET /api/attachments/{id}` 默认要登录（不在 `PUBLIC_PATHS`），归属校验与会话同一口径（404）；前端 fetch 成 blob 再转 objectURL。**刻意不做 `?token=` 兜底**（长期凭证进 URL / 日志 / 浏览器历史），也不把 base64 塞进消息 JSON（响应体膨胀几十倍）。出网 `Content-Type` 只能取白名单 5 种图片 MIME + `nosniff`，改名上传的 HTML 不会被当页面执行；**白名单刻意不含 SVG**（能带脚本）。
19. **上传校验两道**：`spring.servlet.multipart.max-file-size`（413）与 `AttachmentService` 的 5MB / MIME / 张数校验（400 中文文案）是同一个数的两处表达，改一处必须改另一处；前端还有一道同样的本地校验，但后端那道才是兜底。

## 十、开发指南

### 10.1 常用命令

**后端**（工作目录 `chatbot/`）

```powershell
# 启动（Windows 用 local profile 才会加载 application-local.properties）
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
# → http://localhost:8089

.\mvnw.cmd clean package          # 打包，产物 target/chatbot-0.0.1-SNAPSHOT.jar
.\mvnw.cmd test                   # 只有一个 contextLoads()，需要真 MySQL 才能过
```

macOS / Linux 用 `./mvnw spring-boot:run`。启动前提：本机 MySQL 已运行且能连上（或先执行 `sql/init.sql` 建库）。

**前端**（工作目录 `chatbot-web/`）

```sh
npm install
npm run dev          # http://localhost:5173，/api 代理到 :8089
npm run type-check   # vue-tsc --build，只跑类型检查
npm run build        # run-p type-check build-only，产物 dist/
npm run preview      # 本地预览 dist/
```

**手动验证 SSE**：照 `chatbot/README.md`「手动测试」一节的 curl 命令走（登录换 token → 建会话 → 带 token 打 `/chat` 看帧）。

### 10.2 首次跑起来的顺序

1. MySQL 建库：执行 `chatbot/sql/init.sql`（或直接启动后端靠 `ddl-auto=update` + `AdminUserInitializer`）。
2. 配密钥：把 DB 密码和 `llm.api-key` 写进 `chatbot/src/main/resources/application-local.properties`（不入库）。**不配 key 也能跑**，走 `MockLlmClient`。
3. 起后端（带 `--spring.profiles.active=local`），起前端 `npm run dev`。
4. 用 `admin` / `admin` 登录，**登录后立刻改密码**。

### 10.3 文档分工原则

完整的启动步骤只在根 `README.md` 出现一次；契约与实现取舍只在对应模块的 README 出现一次；本文是架构索引与事实源，**指路而不复述**。发现同一件事在三处都写了，就是该收敛的信号。

---

## 十一、代码修改后的文档同步清单

**改完代码必须回到本文对应章节更新。** 下表是容易漏掉的联动点：

| 你改了什么 | 必须同步更新 |
|---|---|
| 增删/重命名任何源码文件 | 本文 **2.1 目录树** + **2.2 / 4.1 文件清单**；若该文件是模块入口，还要更新对应 `README.md` 的「目录结构」段 |
| 实体类字段 / 表结构 | 本文 **6.1 表结构** + `chatbot/sql/init.sql`（Hibernate 不会改已有列） |
| 新增 `Role` 枚举值 | `init.sql` 里的 `enum(...)` 需手工 `ALTER TABLE`（脚本末尾备注）+ 本文 6.1 + `entity/Role.java` |
| 增删改 REST 接口、改路径/方法/状态码 | 本文 **7.1 全量接口表** + **7.2 数据形状** + `chatbot-web/src/api.ts` + `chatbot-web/src/types.ts` + `chatbot/README.md`（若涉及契约前提） |
| 改 SSE 事件类型或字段 | `dto/ChatEvent.java` + `chatbot-web/src/types.ts` 的 `ChatStreamEvent` + `api.ts` 的 `dispatchEvent()` + 本文 **7.3** + `chatbot/README.md` 事件契约表 |
| 新增 `@RequireAdmin` 接口 | 本文 **7.1**（鉴权列）+ **九、安全设计要点** 第 11 条 |
| 改 `PUBLIC_PATHS` 白名单 | `config/WebConfig.java` + 本文 2.2（WebConfig 行）+ 3.3 + `chatbot/README.md`「接口」段 |
| 新增配置项 | `application.properties` + 对应 `*Properties` record + 本文 **5.1 表格**（含环境变量名）+ `chatbot/README.md` 配置表 |
| 改端口 | `application.properties` 的 `server.port` + `chatbot-web/vite.config.ts` 代理 target + 根 `README.md` + 本文 5.1/5.2 |
| 改鉴权机制（token 格式、密钥、失效规则） | 本文 **3.2 / 8.2 / 九** + `chatbot/README.md`「接口」段 |
| 新增前端依赖 | `chatbot-web/package.json` + 本文 **一、技术栈** + 4.1（若引入新的渲染方式，例如真的加了 Markdown 渲染，要同时改 `chatbot-web/README.md` 和本文 13.3） |
| 改默认标题文案 | `service/ChatService.java` 的 `DEFAULT_TITLE` + `chatbot-web/src/views/ChatView.vue` 的 `DEFAULT_TITLE` + `ConversationService.create()` + 本文 6.1（**四处必须一致**，前端靠标题字符串识别空会话） |
| 改标题截断长度 | `ChatService.TITLE_MAX_LENGTH` + 本文 2.2 / 6.1 / 8.1 |
| 改 SSE 超时余量 | `ChatService.SSE_TIMEOUT_MARGIN_MS` + 本文 2.2 / 8.1 + `chatbot/README.md`「停止生成」段 |
| 新增全局 CSS 类 | `chatbot-web/src/assets/main.css` + 本文 4.1（main.css 行） |
| 新增页面 | `chatbot-web/src/views/` + `App.vue` 的条件渲染 + 本文 2.1 / 4.1 / 4.2 组件树 + `chatbot-web/README.md`「目录结构」段 |
| 完成「后续待加」里的某项 | 删掉 `chatbot/README.md` 末尾对应条目 + 更新本文十二、十三节 |
| 改会话归属 / 消息分页这类「表结构 + 接口形状」联动 | 6.1 表结构 + `sql/init.sql`（含「已有库升级」段）+ 7.1 / 7.2 + `api.ts` / `types.ts` + `ChatView.vue` + `chatbot-web/README.md` |
| 上述任何一项 | **刷新本文文末「最后更新」日期，并在第十四节变更记录追加一行** |

同步时请保持本文风格：表格化、写「为什么」、标注刻意的设计取舍，不要写成流水账。

---

## 十二、扩展点

1. **换 LLM 服务商**：改 `llm.base-url` + `llm.model` + `llm.api-key` 即可，任何 OpenAI 兼容接口都能直连。服务商不认识 `enable_thinking` 时，把 `application.properties` 里那一行整行注释掉（`LlmProperties.enableThinking` 变 null → 请求体不下发该字段）。要支持非兼容协议，新写一个 `LlmClient` 实现并在 `LlmConfig` 里加分支。
2. **新增角色 / 细粒度权限**：在 `Roles` 加常量，新增注解（如 `@RequireOperator`），在 `AuthInterceptor.requiresAdmin()` 旁边加对应判断。`sys_user.role` 是 int，**不用改列类型**。
3. **新增接口**：`controller/` 下加方法即可，默认要登录；管理员接口加 `@RequireAdmin`。需要当前用户就在形参里声明 `CurrentUser`。
4. **新增前端页面 / 平级功能模块**：`views/` 下加组件 + `router/routes.ts` 里加一条路由记录。带 `meta.moduleId` / `title` / `icon` / `order` 就会自动出现在左侧模块导航条上，带 `meta.requiresAdmin` 守卫就会自动拦成 `/403`——**ModuleNav 与 guards 都不用改**。管理台类视图用 `() => import()` 懒加载，聊天这类落地页保持静态 import。
5. **`chatbot/README.md` 末尾的「后续待加」**：见该文件，已完成的条目会随变更删掉。
6. **多模态往哪扩**：图片输入已通（`image_url` + base64）。下一步自然是把 `data` 换成对象存储 key（多实例部署的前提）、给孤儿附件加定时清理、或按 `usage` 回填做动态 token 预算（把图片 token 也算进去）。

---

## 十三、已知限制、坑与文档偏差

### 13.1 功能限制

1. **SSE 单向**：客户端断开后无法恢复，只能重新发起请求；已生成部分会入库，刷新能看到。
2. **无自动化测试**：`src/test` 只有空的 `contextLoads()`，且需要真 MySQL。原本还有个手动脚本 `chatbot/test_sse.py`（gitignore、不入库），**已于 2026-09-24 删除**；现在 SSE 全链路只剩 curl 手测。
3. **联网搜索拿不到引用来源**：OpenAI 兼容协议不支持「返回搜索来源 / 角标标注」（只有 DashScope 原生协议支持），所以界面上没有「参考了哪几个网页」；要做引用 UI 得换协议，是另一个工程量。
4. **`LlmClient` Bean 启动时定型**（Mock vs OpenAI 兼容实现二选一，运行期改 `llm.api-key` 必须重启）；但**模型 id 已可每请求切换**（`llm.available-models` 白名单 + 请求体 `model`），不再是「全服务只有一个模型」。
5. **图片占的 token 不计入 `llm.max-history-tokens` 预算**：估算器只认文字，视觉 token 由分辨率决定、本地算不准。带图会话的真实上下文大小以用量里的 `prompt_tokens` 为准（实测一张 320×200 的图约 90 token，且每轮历史都会重发）。要严格控制成本得改成按 usage 回填的动态预算。
6. **孤儿附件没有定时清理**：传上来了但没随消息发出去的附件会一直留在库里，直到所属会话被删（前端「空会话回收」会顺手带走）。项目没有 `@Scheduled`，加清理任务得先引调度。
7. **图片只进 Chat Completions 的 `image_url`，不做 OCR / 图像检索 / 生成**：要「以图搜图」或生图是另一个工程量（百炼有独立的多模态接口）。

### 13.2 容易踩的坑

1. **`AGENTS.md` 被 gitignore**（`.gitignore` 第 75 行）：它只在本地工作区存在，不会随仓库分发。若希望协作规则对所有克隆者生效，需要把它从 `.gitignore` 移除并提交。
2. **别再引用 `chatbot/test_sse.py`**：它连同 `.gitignore` 里那条规则已于 2026-09-24 删除（用户明确要求不要这个脚本）。文档里凡提到它的地方都已清掉，新写的内容也别再指回这个文件。
3. **`application-local.properties` 里存着真实的百炼 API Key**：文件本身不入库，但**不要把它的内容复制进任何会提交的文件、日志或对外回答**。
4. **`auth.token-secret` 少于 32 字符后端启动直接失败**，报错在 `TokenService` 构造函数里而不在配置校验阶段——看到 `IllegalStateException: auth.token-secret 至少 32 字符` 不要往别处找。
5. **改密码会踢掉其他所有设备**（`iat` vs `password_changed_at`），这是设计意图。前端改密码成功后**必须 `setSession()` 换上新 token**，否则自己也会被踢。
6. **`iat` / `exp` / `password_changed_at` 全部按毫秒比较**，`password_changed_at` 列精度必须是 `datetime(6)`。改成 `datetime` 会让失效判断在「改密码那一秒」漏判。
7. **`GlobalExceptionHandler` 刻意没有 catch-all**：加一个 `@ExceptionHandler(Exception.class)` 会破坏 `/chat` 的 SSE 错误路径（往已提交的 `text/event-stream` 里再写 JSON）。
8. **`GlobalExceptionHandler` 每个方法都显式设 `Content-Type: application/json`**：去掉会因 `Accept: text/event-stream` 触发内容协商 406，把真实状态码盖掉。
9. **`MessageRepository.deleteByConversationId` 需要调用方自带事务**（`@Modifying` + JPQL），`ConversationService.delete()` 上有 `@Transactional`，别在别处裸调。
10. **前端 `TextDecoder` 不能每帧新建**，`decode(value, { stream: true })` 是中文/emoji 不乱码的前提。
11. **`MessageBubble.vue` 的 `:open="message.streaming || undefined"`**：写成 `false` 无效——HTML 的 `open` 属性只要存在就生效。
12. **`ChatView` 里必须从数组取回响应式代理再改**（`messages.value[len-1]!`），直接改 push 进去的原始对象不触发视图更新。
13. **`auth.ts` 不能 import `api.ts`**：会和 `api.ts → auth.ts` 形成循环依赖。
14. **非 BMP 字符（emoji）在增量流里会被切成两半**：后端靠 `ChatService.SurrogateBuffer`、Mock 靠按码点切分来避免 `?` 乱码。改这两处的切分逻辑前请先读懂注释。
15. **`llm.request-timeout-seconds` 是整轮上限而非空闲超时**：JDK HttpClient 到点直接关流，表现为 `IOException("closed")`，此前思考全部作废但**照样计费**。曾实测思考到 31407 字、正式回答 0 字。
16. **思考内容按输出 token 计费**：曾实测一轮思考 2.4 万字、正式回答只有 1 个字。关闭思考可把首字延迟从 2.7 秒降到 0.4 秒。取舍详见 `chatbot/README.md`。
17. **检索代码时排除** `chatbot/target/`、`chatbot-web/node_modules/`、`chatbot-web/dist/`、`.idea/`——`target/` 下有和源码同名的 `.class`，容易污染搜索结果。
18. **`ChatService.DEFAULT_TITLE` 与前端 `ChatView.DEFAULT_TITLE` 是隐式契约**：前端靠「标题等于『新的对话』」识别空会话并静默删除。任一侧改了文案而另一侧没改，会导致空会话删不掉、或把有内容的会话误删。
19. **qwen3.8-max / qwen3.8-flash 的 `preserve_thinking` 默认 true**：要求历史 assistant 消息把 `reasoning_content` 完整回传，且不支持拼进 `content` 回传。我们把存库的思考随历史带上（`LlmMessage.reasoningContent`）；缺了不报错但多轮推理质量打折。
20. **`thinking_budget` 与 `reasoning_effort` 不能同时设置**（qwen3.8 系），同时设会报错。我们只发 `thinking_budget`；档位 4096 / 16384 对齐官方 low / medium 映射，131072 是 qwen3.8 系默认值、也正好是 qwen3.6-flash 的思维链上限。
21. **本地百炼 wiki 快照曾缺 `qwen3.8-flash`**（flash 线只到 qwen3.7），导致一度误判该模型不存在；以官方 OpenAI 兼容 Chat 文档为准（文档里三处点名 qwen3.8-flash）。查模型存不存在别只信本地快照。
22. **system 消息不占 `llm.max-history-messages` 名额**：它是人设不是对话，截历史不该把它截掉；但它**计入输入 token**，人设写两千字每轮都烧两千字的输入钱。 |
23. **联网搜索默认关且只暴露开关**：turbo 策略约 3 元/千次 + 检索内容带来的输入 token；`qwen3.8-max` / `qwen3.8-flash` 在 Chat Completions 下不支持 `search_strategy: agent`（要 agent 得走 Responses API），所以界面不给策略选择。 |
25. **`LlmContentPart.isImage()` 必须 `@JsonIgnore`**：Jackson 会把 `isXxx()` 当 getter 序列化成 `"image": true`，而百炼对 content 数组元素做 pydantic 严格校验，多一个不认识的字段就整请求 400，错误文案还是一串 validation errors，根因极难看出。同理 `LlmMessage` 上的 `text()` / `imageCount()` / `hasImages()` 靠「没有 get/is 前缀」才没被序列化出去，改名时别加前缀。
26. **一个附件只能挂一条消息**：`linkToMessage` 的 UPDATE 带 `message_id IS NULL`，重复提交同一个 id 会 400「附件不可用」。副作用是「发送失败后重试」必须重新选图（旧附件已挂在失败那条用户消息上）；前端发送成功后会清空 `pending`，正常路径感知不到。
27. **多模态校验的顺序是刻意的**：`chat()` 里「附件校验 → 模型白名单 → 落库」，保证非法请求不留半条消息；但「历史里有旧图 + 用户换了非视觉模型」只能在落库后查出来，此时 400 是**可接受的**（用户消息还在，换回视觉模型点重新生成即可）。反过来 `regenerate()` 必须先查再删，否则旧回答白删。
28. **上传超限是 413 不是 400**：`MaxUploadSizeExceededException` 在 multipart 解析阶段抛出、到不了 controller，必须由 `GlobalExceptionHandler` 专门接住，否则用户只看到 500 + Spring 默认错误页。
29. **history 模式生产部署必须配回退**：`createWebHistory()` 下直接访问 `/admin/users` 或在该页刷新，请求会真的打到静态服务器；不配 `try_files $uri /index.html`（nginx）就是服务器 404。开发期 Vite 自带回退，所以本地永远发现不了这个问题。
30. **`ensureSession()` 的 promise 按页面加载缓存**：登录响应自带 user，登录后不需要重验；但同一次页面加载内后端改了角色，界面不会感知，要等下次刷新。想实时就得轮询 `/api/auth/me`，目前刻意不做。
31. **`?next=` 必须过 `safeNextPath()`**：它是 URL 上的外部可控参数，`//evil.com` 这种协议相对 URL 不挡掉就是一个现成的开放重定向。
32. **掉登录态回登录页只有 App.vue 的 `watch(isAuthenticated)` 一个口子**：别在组件里各自写 `router.replace('/login')`，否则 401、退出登录、token 被别的标签页清掉这三条路径会各走各的逻辑。
24. **历史 token 预算是估算值**：没有分词器可用，按「中文 1 字 1 token、其余 4 字符 1 token」近似；预算是保护性上限不是精确配额。每轮真实上下文大小以用量里的 `prompt_tokens` 为准（界面已显示），两者对不上时信后者。 |

### 13.3 文档偏差记录（2026-09-21 已全部修正，2026-09-24 追加第 5 条）

首次全量通读时发现三份 README 有 4 处与代码不符，现已按实际情况改写。**这张表留着是为了说明「为什么这几处的措辞是现在这样」**，不要把它们又改回原来的说法。

| # | 位置 | 原说法（错） | 已改为 |
|---|---|---|---|
| 5 | 本文 2.2 controller 表下的注意 | 「`ConversationController` 和 `ChatController` 都没有 `CurrentUser` 形参，会话操作不做归属校验」 | 会话隔离（2026-09-24 第二次变更）落地后每个方法都带 `CurrentUser`、service 层做归属校验；本次改为「每个方法都声明 `CurrentUser`」，并保留一句说明那是隔离前的旧状态 |
| 1 | `chatbot-web/README.md`「SSE 流式解析」末句 | `MessageBubble.vue` 负责 **Markdown 渲染**、思考折叠和打字效果 | 明确写「正文是纯文本（`{{ message.content }}` + `white-space: pre-wrap`），项目没有引入任何 Markdown 渲染库」，并指出要加 Markdown 得先引依赖再同步本文 4.1 与 13.1 |（**2026-09-24 起正文已改为 Markdown 渲染**，见 4.1 的 `MarkdownContent.vue`；这两行保留是为了说明当初为什么那样改）
| 2 | `chatbot-web/README.md`「目录结构」 | `MessageBubble.vue  消息气泡，区分 user / assistant` | 补上「纯文本渲染（无 Markdown）+ 思考折叠 + 打字光标」 |（同上：`MessageBubble` 现在把助手消息交给 `MarkdownContent`，用户消息仍纯文本）
| 3 | `chatbot/README.md`「错误响应体」正文 **和** 配置表 | 由 `GlobalExceptionHandler` + **`server.error.include-message=always`** 保证（配置表里还列了这一项） | 改为「由 `GlobalExceptionHandler` 自己组装 `ErrorResponse`，**不依赖** `server.error.include-message`」，并**删掉了配置表里那一行**——`application.properties` 里从来没有这项配置 |
| 4 | 根 `README.md`、`chatbot/README.md`、`chatbot-web/README.md` | 「multi-user auth」/「多用户」容易被读成「每个用户有自己的会话」 | 三份 README 各加一处显式说明：多用户只指鉴权与用户管理，会话全站共享、可互相读写删除，并指向本文 13.1 |（**该说明已于 2026-09-24 被「会话按用户隔离」取代**：三份 README 又改写了一次，侧边栏现在就是「我的会话」）

> 顺带在根 `README.md` 的 Documentation Map 里补了一节 **For AI agents**，指向 `AGENTS.md`，并说明它被 gitignore、新克隆的仓库里看不到时应直接以本文为准。

---

## 十四、变更记录

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 初版：架构、模块、数据库、API、安全、扩展点、已知限制 |
| 2026-09-21 | 全量重写为「接手即用」的事实源：新增五分钟速览与检索入口速查、逐文件清单、Bean 装配表、三类请求调用链、前后端编码约定、SSE 解析要点、ChatView 状态与行为表、列级表结构说明、API 请求/响应 JSON 示例与状态码语义、五条核心流程时序、第十一节文档同步清单、18 条踩坑记录、3 条文档偏差。同时更正：会话不按用户隔离、`MessageBubble` 无 Markdown 渲染、`AGENTS.md` 与 `test_sse.py` 被 gitignore |
| 2026-09-21（第二次） | 修正三份 README 共 4 处与代码不符的表述（`MessageBubble` 的 Markdown 渲染、`server.error.include-message`、会话不按用户隔离），并把 13.3 从「待修正的偏差」改写为「已修正记录」；根 `README.md` 增加 For AI agents 指引与更准确的文档地图 |
| 2026-09-24 | 删除手动测试脚本 `chatbot/test_sse.py` 及 `.gitignore` 中对应规则（`AGENTS.md` 因此从第 78 行移到第 75 行）。同步清掉本文 5 处引用：五分钟速览、2.1 目录树、2.2 文件清单、5.3 `.gitignore` 条目表、7.4 手动测试、10.1 命令块；改写 13.1 第 7 条与 13.2 第 2 条。**编号刻意保留**：13.2 仍是 18 条，`AGENTS.md` 里按「第 7 / 8 / 17 / 18 条」的交叉引用不受影响。|

| 2026-09-24（第二次） | **会话按用户隔离 + 消息分页**。`conversation` 加 `owner_id`（NOT NULL + 外键 `fk_conversation_owner` + 复合索引 `idx_conversation_owner_updated`）；3~7 号接口接 `CurrentUser`，归属校验统一在 service 层且**一律 404**；`GET /{id}/messages` 从数组改成 `MessagePageVO{items, beforeId, hasMore}` 游标分页（默认 50、上限 200），`ChatService.recentHistory` 的 LIMIT 下推到 SQL；前端加「加载更早的消息」按钮 + 滚动位置补偿。旧会话按用户要求全部清空，`init.sql` 新增「已有库升级」段记录迁移 SQL。13.1 删掉第 1、2 条并重排为 9 条，十二删掉第 5、6 条；**13.2 编号不动**，`AGENTS.md` 里按「第 7 / 8 / 17 / 18 条」的交叉引用不受影响 |
---
| 2026-09-24（第三次） | **配置收紧（P0 收尾）**：`spring.datasource.url` / `username` 包上 `DB_URL` / `DB_USERNAME` 占位符；新增 `cors.allowed-origins`（`CORS_ALLOWED_ORIGINS`，默认只有 Vite 的 5173）+ `config/CorsProperties.java`，`WebConfig` 改读它，**留空启动失败**而不是退化成 `*`。13.1 删掉 CORS 全开与 datasource 硬编码两条，重排为 7 条；九加第 16 条。实测：5173 预检 200 带 ACAO 头、陌生源 403 无 ACAO 头、无 Origin 的同源调用不受影响、`DB_USERNAME` 环境变量确实生效 |

| 2026-09-24（第四次） | **P1-1：Markdown 渲染 + 代码高亮 + 一键复制**。新增 `src/lib/markdown.ts`（markdown-it `html:false` + `breaks:true` + highlight.js common 子集 + DOMPurify 消毒；自定义 fence 给代码块加语言标签和复制按钮）与 `components/MarkdownContent.vue`（委托监听处理复制、流式光标用 CSS `::after` 挂在最后一个块末尾）；`MessageBubble` 里助手消息改走 Markdown、**用户消息刻意保持纯文本**。踩到一个坑：markdown-it 给围栏代码的 `<code>` 只加 `language-xx`、不加 `hljs` 类，行内代码样式会命中它画出逐行灰块，已用 `pre code` 覆盖修复。13.1 删掉「无 Markdown 渲染」重排为 6 条 |
| 2026-09-24（第五次） | **P1-2：会话重命名**。新增 `PUT /api/conversations/{id}/title`（`RenameConversationRequest`，title trim 后入库、1~100 字）；repository 用 `@Modifying` JPQL 只改 title，**刻意不触发 `@PreUpdate`**——改名不是「活动」，不该把会话顶到列表最前面；越权改名影响行数 0 → 404。前端侧边栏双击标题行内编辑（Enter / 失焦提交、Esc 取消），成功后只覆盖本地那一条。13.1 第 2 条去掉「无会话重命名」，`chatbot/README.md`「后续待加」同步删条目 |
| 2026-09-24（第六次） | **P1-3：思考过程持久化**。`message` 加可空列 `reasoning`（LONGTEXT）；`ChatService` 在流式期间把思考全文攒进 `reasoningFull`，结束时随助手消息一起入库（中断 / 失败走 `savePartial` 也带上）；`MessageVO` 加 `reasoning` 并标 `@JsonInclude(NON_NULL)`，没思考时字段不下发；前端 `types.ts` / `ChatView` 把它读回 `UiMessage.reasoning`，刷新后折叠块可展开重读。**可空列迁移，旧消息保持 NULL，不需要清数据**（`init.sql`「已有库升级」第二段）。13.1 删掉「思考过程不持久化」重排为 5 条 |
| 2026-09-24（第七次） | **P1-4：重新生成**。新增 `POST /api/conversations/{id}/regenerate`（SSE，请求体可省略）：`ConversationService.dropLastAssistantMessage()` 删最后一条助手消息并返回它前面的用户消息正文（最后一条不是助手消息 / 空会话 → 400），`ChatService` 把 SSE 骨架抽成 `startStream()` 供 chat 与 regenerate 共用。前端最后一条助手气泡挂弱化文字按钮，点击后本地 pop 旧回答再接流；`api.ts` 把 SSE 解析抽成 `consumeSse()` 供 `streamChat` / `streamRegenerate` 共用。实测：重跑后消息条数不变、旧回答 id 被新 id 替换、用户消息不动。13.1 第 2 条去掉「无重新生成」 |
| 2026-09-24（第八次） | **P1-5 用量统计 + 模型 / 思考强度可选**。`message` 加 `model` / `prompt_tokens` / `completion_tokens` / `reasoning_tokens` 四列（可空，拿不到就 NULL）；流式请求开 `stream_options.include_usage`，用量帧在「空 choices」之前解析；`done` 事件附带 model + 用量供前端当场回填。新增 `GET /api/llm/options` 与配置 `llm.available-models`（默认含 **qwen3.8-flash**——本地 wiki 快照缺它，官方 Chat 文档确认存在）；请求体 `model` 走白名单校验、`thinkingBudget` 校验 1~262144 且思考关着时丢弃；历史 assistant 消息回传 `reasoning_content`（qwen3.8 系 preserve_thinking 默认 true）。界面：输入条加模型 / 思考强度两个选择器（档位 4096/16384/131072 对齐官方 reasoning_effort 映射），助手气泡加用量行。13.1 第 7 条改写、13.2 追加 19~21 条 |
| 2026-09-24（第九次） | **每用户系统提示词**。`sys_user` 加可空列 `system_prompt`；`CurrentUser` 带上它（AuthInterceptor 本就回表），`ChatService.recentHistory` 在非空时把它作为 system 消息放在历史最前面（不占历史条数名额）；新增 `PUT /api/users/me/system-prompt`（2000 字上限、全空白=清除、不换发 token）；`UserVO` 加 `systemPrompt` 但管理员用户列表恒为 null；前端新增 `SystemPromptDialog` + 顶栏入口，保存后覆盖本地登录态、下一条消息立即生效。接口表 13 个；九加第 17 条、13.2 加第 22 条 |
| 2026-09-24（第十次） | **前端视觉重做**（参考 ChatGPT / Claude 现行界面）：暖中性纸感配色 + 暗色深墨、内容列居中限宽 760px、助手消息去气泡加头像、用户消息改浅色 pill、顶栏收敛为「标题 + 头像下拉菜单」（新增 `UserMenu.vue`）、合成输入框改胶囊卡片（自动长高 + 圆形发送/停止）、空状态问候语 + 建议 chips、侧边栏品牌标 + hover 浮出操作、登录页光晕背景卡片、全局细滚动条 / 选区 / 焦点环 / 弹窗入场动画。纯样式与模板改动，接口与数据零变化 |
| 2026-09-24（第十一次） | **联网搜索**。`LlmCallOptions` / `ChatRequest` / `RegenerateRequest` 加 `enableSearch`，OpenAI 兼容请求体在开启时下发 `enable_search: true`（策略固定默认 turbo）；输入条加「联网」pill（默认关、存 localStorage）；Mock 回显「【Mock 联网】」一行。实测：mock 链路开关生效；真实 key 全链路一发请求返回带当天日期的实时天气。13.1 加第 5 条（兼容协议无引用来源）、13.2 加第 23 条（计费与 agent 策略限制） |
| 2026-09-24（第十二次） | **上下文按 token 预算截断**。新增 `llm.max-history-tokens`（默认 24000，`LLM_MAX_HISTORY_TOKENS`）：`recentHistory` 从最近一条往前累加估算 token（思考也计入），与条数上限谁先满足谁生效，一次最多扫 200 条，且至少保留最新一条。实测：30 条 2000 字长消息只送 12 条进模型（prompt_tokens=5513，与推算吻合）。13.2 加第 24 条（估算是近似，真实值看 prompt_tokens） |
| 2026-09-24（第十三次） | **图片输入（多模态）**。新增第 4 张表 `attachment`（图片字节存 LONGBLOB）+ `entity/Attachment.java` / `repository/AttachmentRepository.java` / `service/AttachmentService.java` / `controller/AttachmentController.java` / `dto/AttachmentVO.java` / `llm/LlmContentPart.java`；接口 13 → 15 个（`POST /{id}/attachments` multipart、`GET /api/attachments/{id}` 带鉴权出字节）。`LlmMessage.content` 从 String 变 Object（纯文本发字符串、带图发 `[{image_url},{text}]` 数组），`recentHistory` 把附件 base64 内联进历史；`ChatRequest` 加 `attachmentIds` 并**去掉 `@NotBlank`**（纯图片提问合法）；新增 `llm.vision-models` 白名单 + `LlmOptionsVO.visionModels`，带图打到非视觉模型直接 400。前端：`AttachmentThumb.vue`（fetch blob → objectURL，自己 revoke）、输入框「图片」按钮 + 粘贴 + 拖拽、待发送缩略图条、气泡缩略图。配置加 multipart 上限，`GlobalExceptionHandler` 接 413 / 缺字段。实测：4 个模型在兼容协议下都正确读图（320×200 测试图答对形状与颜色）、多轮追问仍带图、重新生成自动带图、9 条负路径（跨会话 / 重复用 / 非视觉 / 超限 / 空消息）全部按预期报错。13.1 删掉「无多模态」重排为 7 条，13.2 加 25~28 条，13.3 追加一条旧偏差 |
| 2026-09-25（第十四次） | **前端传输层抽离**（为「和聊天平级的功能模块」铺路，纯重构、零行为变化）。新增 `chatbot-web/src/api/client.ts`，把 `api.ts` 里的 `BASE` / `request<T>()` / `withAuth()` / `extractErrorMessage()` **原样搬过去并导出**（`BASE` 更名 `API_BASE`）；`api.ts` 只剩接口清单，改动为「文件头换成两行 import + 3 处 `${BASE}` 改名」。401 仍然调 `clearSession()`，登录态与接口行为与改前完全一致。4.4 第 6 条同步改写：新模块另开 `src/api/<模块>.ts`。`npm run type-check` 与 `npm run build` 均通过 |
| 2026-09-25（第十五次） | **引入 vue-router@4：URL 即模块**。新增 `router/{index,routes,guards,paths}.ts`、`session.ts`、`layouts/AppShell.vue`、`components/ModuleNav.vue`、`components/icons/IconChat.vue`、`views/{ForbiddenView,NotFoundView}.vue`；`main.ts` 挂 router，`App.vue` 从「三分支权限闸门」改成 `RouterView` + 对 `isAuthenticated` 的全局 watch（掉登录态回登录页的唯一兜底，同时覆盖 401 与主动退出登录），`LoginView` 登录成功后 `router.replace(safeNextPath(next) ?? /chat)`。URL：`/login`、`/chat`、`/403`、404 兜底；平级模块挂 `AppShell` 子路由，导航条目由 `routes.ts` 的 meta 派生（加模块不改导航与守卫）。`ChatView.vue` **零改动**。实测：`/` → `/chat`、未登录 `/chat` → `/login?next=/chat`、`?next=//evil.com` 被拒、SSE 全链路正常、退出登录回登录页、未知路径 404 页带导航条。〇 的「刻意没有 vue-router」删除、一 技术栈加 vue-router、13.2 加 29~32 条 |
*最后更新：2026-09-25*

