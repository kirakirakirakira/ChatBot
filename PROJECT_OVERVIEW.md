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
| 数据库 | MySQL 8，库名 `chatbot`，共 **3 张表**：`sys_user` / `conversation` / `message` |
| LLM | `llm.api-key` 有值 → `OpenAiCompatibleLlmClient`；为空 → `MockLlmClient`（本地假回复，链路照样能跑通） |
| 鉴权 | 自签 HMAC-SHA256 token（不用 JWT 库、不用 Spring Security starter），白名单**只有** `POST /api/auth/login` |
| 默认账号 | `admin` / `admin`（`sql/init.sql` 的 `INSERT IGNORE` 与 `AdminUserInitializer` 二选一兜底创建） |

**本项目刻意没有的东西**（找不到不是你没找到，是真的没有）：

- 没有 `vue-router`：页面切换 = `App.vue` 换根组件（`LoginView` ↔ `ChatView`）。
- 没有状态管理库（Pinia/Vuex）：登录态是 `src/auth.ts` 里的模块级 `ref`。
- 没有 `axios`：全部用原生 `fetch`。
- 没有 Markdown 渲染库：`MessageBubble.vue` 用 `white-space: pre-wrap` 输出**纯文本**。
- 没有 `spring-boot-starter-security`：只引了 `spring-security-crypto` 拿 BCrypt。
- 没有 WebSocket：流式回复用 SSE（`SseEmitter`）。
- 没有分页：会话列表与消息列表都是全量查询。
- 没有会话级用户隔离：`conversation` 表没有 `user_id`，**所有登录用户看到同一份会话列表**（详见第十三节）。
- 没有单元测试：`src/test` 下只有一个空的 `contextLoads()`；真实验证靠 `chatbot/test_sse.py`（已 gitignore）。

**检索入口速查**（想改 X → 直接看 Y）：

| 我想… | 看这里 |
|---|---|
| 加/改一个 REST 接口 | `chatbot/src/main/java/com/chatbot/chatbot/controller/` + 对应 `service/` + `dto/`，前端再改 `chatbot-web/src/api.ts` |
| 改 SSE 事件形状 | `dto/ChatEvent.java`（后端产出）+ `chatbot-web/src/api.ts` 的 `dispatchEvent()` + `types.ts` 的 `ChatStreamEvent` |
| 改登录/权限 | `auth/` 整个包 + `config/WebConfig.java`（白名单在这里）+ 前端 `src/auth.ts`、`src/App.vue` |
| 改表结构 | `entity/` 三个实体类（**唯一事实源**）→ 同步 `chatbot/sql/init.sql` |
| 换模型/换服务商 | `chatbot/src/main/resources/application.properties` 的 `llm.*` + `llm/LlmProperties.java` |
| 改 LLM 请求体/解析 | `llm/OpenAiCompatibleLlmClient.java` |
| 改流式编排、取消、自动标题、历史截断 | `service/ChatService.java`（全项目最复杂的一个类） |
| 改界面配色/圆角/间距 | `chatbot-web/src/assets/main.css`（CSS 变量 + 登录/弹窗共用件），组件内 `<style scoped>` |
| 改聊天主界面行为 | `chatbot-web/src/views/ChatView.vue`（前端最大的文件，约 700 行） |
| 加配置项 | `application.properties` → 对应 `*Properties` record（`AuthProperties` / `LlmProperties`）→ 第五节表格 |
| 改端口/代理 | 后端 `application.properties` 的 `server.port`，前端 `chatbot-web/vite.config.ts` 的 `server.proxy` |

---

## 一、项目概述与技术栈

全栈 AI 聊天应用：多用户登录、多会话管理、流式（SSE）对话，对接阿里云百炼 DashScope（OpenAI 兼容模式）或任何其他 OpenAI 兼容模型服务。

> **「多用户」= 登录与用户管理，不是数据隔离。** 会话是全站共享的（`conversation` 表没有 `user_id`），详见 13.1 第 1 条。

| 层级 | 技术与精确版本 | 出处 |
|---|---|---|
| 后端语言 | Java **26** | `chatbot/pom.xml` 的 `<java.version>` |
| 后端框架 | Spring Boot **4.1.1**（`spring-boot-starter-parent`） | `chatbot/pom.xml` |
| 后端依赖 | `starter`、`starter-web`、`starter-data-jpa`、`starter-validation`、`mysql-connector-j`(runtime)、`spring-security-crypto`、`starter-test`(test) | `chatbot/pom.xml` |
| JSON | Spring Boot 4 自带的 **Jackson 3**：注入类型是 `tools.jackson.databind.ObjectMapper`；注解仍用 `com.fasterxml.jackson.annotation.*`（Jackson 3 兼容 Jackson 2 注解包） | `TokenService` / `ChatService` / `OpenAiCompatibleLlmClient` / `ChatEvent` |
| HTTP 客户端 | JDK 原生 `java.net.http.HttpClient`（**没有** RestTemplate/WebClient/OkHttp） | `llm/OpenAiCompatibleLlmClient.java` |
| 数据库 | MySQL 8.0，`utf8mb4` / `utf8mb4_unicode_ci` | `chatbot/sql/init.sql` |
| ORM | Spring Data JPA + Hibernate，`ddl-auto=update`，`open-in-view=false` | `application.properties` |
| 前端 | Vue **3.5.40**（唯一运行时依赖）、TypeScript **~6.0.0**、Vite **^8.1.5** | `chatbot-web/package.json` |
| 前端工具链 | `@vitejs/plugin-vue` ^6.0.8、`vite-plugin-vue-devtools` ^8.1.5、`vue-tsc` ^3.3.7、`npm-run-all2` ^9.0.2、`@vue/tsconfig` ^0.9.1、`@tsconfig/node24`、`@types/node` ^24.13.3 | `chatbot-web/package.json` |
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
│   ├── test_sse.py             # SSE 手动测试脚本（gitignore，仅本地）
│   ├── sql/init.sql            # 建库建表 + 种子管理员
│   └── src/
│       ├── main/java/com/chatbot/chatbot/
│       │   ├── ChatbotApplication.java
│       │   ├── auth/           # 7 个文件：token 签发校验 + 拦截器 + 当前用户注入 + 角色
│       │   ├── config/         # 5 个文件：Web/CORS、BCrypt、LLM Bean、种子管理员、全局异常
│       │   ├── controller/     # 4 个文件：Auth / Conversation / Chat / User
│       │   ├── dto/            # 9 个文件：请求体、响应 VO、SSE 事件、统一错误体
│       │   ├── entity/         # 4 个文件：User / Conversation / Message / Role
│       │   ├── llm/            # 6 个文件：客户端抽象 + OpenAI 兼容实现 + Mock + 配置
│       │   ├── repository/     # 3 个文件：Spring Data JPA 接口
│       │   └── service/        # 3 个文件：ChatService / ConversationService / UserService
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
        ├── main.ts             # createApp(App).mount('#app')
        ├── App.vue             # 根组件 = 权限闸门
        ├── api.ts              # fetch 封装 + 401 处理 + SSE 读流
        ├── auth.ts             # 登录态（模块级 ref + localStorage）
        ├── types.ts            # 与后端 DTO/VO 一一对应的类型
        ├── assets/main.css     # 全局 CSS 变量 + 登录/弹窗共用件
        ├── views/              # LoginView.vue、ChatView.vue
        └── components/         # ConversationSidebar、MessageBubble、ChangePasswordDialog、UserListDialog
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
| `WebConfig.java` | `WebMvcConfigurer`。① CORS：`/api/**`，`allowedOriginPatterns("*")`，方法 GET/POST/PUT/DELETE/OPTIONS，头 `*`；② 拦截器：`addPathPatterns("/api/**").excludePathPatterns(PUBLIC_PATHS)`，**`PUBLIC_PATHS` 只有 `/api/auth/login` 一项**（白名单模式：新接口默认要登录）；③ 注册 `CurrentUserArgumentResolver`。改免登录路径就改这个文件的常量。 |
| `AuthConfig.java` | `@EnableConfigurationProperties(AuthProperties.class)` + 声明 `PasswordEncoder` Bean = `BCryptPasswordEncoder()`（默认 strength 10）。因为没引 starter-security，这个 Bean 必须自己声明。 |
| `LlmConfig.java` | `@EnableConfigurationProperties(LlmProperties.class)` + 声明 `LlmClient` Bean：`apiKey` 为空/空白 → `MockLlmClient`，否则 → `OpenAiCompatibleLlmClient`。**Bean 在启动时定型，改 key 必须重启。** |
| `AdminUserInitializer.java` | `ApplicationRunner`。`sys_user` 表 `count() == 0` 时创建初始管理员（用户名/密码取 `AuthProperties`）。很多人不跑 `init.sql`、直接靠 `ddl-auto=update` 启动，那样表建出来但没账号，谁都登不进去。条件只有 count==0，不会覆盖已有账号或重置改过的密码。日志里**故意不打印密码**。 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice`。处理 `ResponseStatusException`（保留状态码，reason 进 message）、`MethodArgumentNotValidException`（拼字段错误 → 400）、`HttpMessageNotReadableException`（→ 400「请求体不是合法的 JSON」）。统一返回 `ErrorResponse`。**两个关键取舍**：① 刻意不加 catch-all `Exception` 处理器——`/chat` 的错误已在异步线程里用 `ChatEvent.error` 发给前端，catch-all 会往已提交的 `text/event-stream` 里再写一份 JSON；② 每个 handler 都显式 `contentType(APPLICATION_JSON)`，否则内容协商因 `Accept: text/event-stream` 变成 406，把真实状态码盖掉。 |

**controller/ — REST 入口（薄，只做参数绑定与委派）**

| 文件 | 前缀 | 端点 |
|---|---|---|
| `AuthController.java` | `/api/auth` | `POST /login`（`@Valid LoginRequest` → `LoginResponse`；全站唯一免登录接口）、`GET /me`（`CurrentUser` → `UserVO`） |
| `ConversationController.java` | `/api/conversations` | `POST`（→ 201 + `ConversationVO`）、`GET`（→ `List<ConversationVO>`）、`GET /{id}/messages`（→ `List<MessageVO>`）、`DELETE /{id}`（→ 204） |
| `ChatController.java` | `/api/conversations` | `POST /{id}/chat`，`produces = TEXT_EVENT_STREAM_VALUE`，`@Valid ChatRequest` → `SseEmitter` |
| `UserController.java` | `/api/users` | `PUT /me/password`（→ 新的 `LoginResponse`）、`GET`（`@RequireAdmin` → `List<UserVO>`）。路径里的 `me` 就是「只能是自己」，**不接受 userId 参数**。 |

> 注意：`ConversationController` 和 `ChatController` **都没有 `CurrentUser` 形参**，会话操作不做归属校验——这是第十三节记录的限制，不是遗漏阅读。

**dto/ — 全部是 record，请求体带 jakarta.validation 注解**

| 文件 | 形状 / 校验 |
|---|---|
| `LoginRequest.java` | `(username, password)`，各 `@NotBlank`。**刻意不加 `@Size`/`@Pattern`**：格式校验只会把「密码错了」变成「格式不合法」，白给爆破的人送信息。 |
| `LoginResponse.java` | `(token, tokenType="Bearer", expiresIn秒, user)`，静态工厂 `of(token, expiresIn, user)`。登录与改密码共用。 |
| `ChangePasswordRequest.java` | `(oldPassword @NotBlank, newPassword @NotBlank @Size(6..64))`。`oldPassword` **只有 `@NotBlank`、不限长度**：种子管理员密码 `admin` 只有 5 位，给它套 `min=6` 会让人永远改不了密码。 |
| `ChatRequest.java` | `(message @NotBlank, enableThinking Boolean)`。`enableThinking` 为 `null` 时沿用服务端 `llm.enable-thinking`；非 null 时只对本条消息生效。 |
| `ChatEvent.java` | SSE 事件体 `(type, content, messageId)` + `@JsonInclude(NON_NULL)`。静态工厂：`reasoning(content)` / `delta(content)` / `done(messageId)` / `error(content)`。**错误文案在 `content`，不是 `message` 字段。** |
| `UserVO.java` | `(id, username, role, roleLabel, createdAt)`。**没有 password 字段**：BCrypt 哈希也不能出网。`roleLabel` 由后端 `Roles.label()` 给出，前端不用再维护 0/1 映射。 |
| `ConversationVO.java` | `(id, title, createdAt, updatedAt)` |
| `MessageVO.java` | `(id, role小写字符串, content, createdAt)` |
| `ErrorResponse.java` | `(timestamp, status, error, message, path)`，字段与 Spring 默认 `/error` 输出一致。 |

**entity/ — JPA 实体，表结构的唯一事实源**

| 文件 | 表 | 要点 |
|---|---|---|
| `User.java` | `sys_user` | 表名用 `sys_user` 而不是 `user`：MySQL 里 `user` 既是关键字又是函数名，处处要加反引号。字段 `id`(IDENTITY)、`username`(非空,50,唯一约束 `uk_sys_user_username`)、`password`(非空,100；BCrypt 固定 60 字符，留余量给以后换算法)、`role`(Integer 非空)、`createdAt`(`@PrePersist` 写入,`updatable=false`)、`passwordChangedAt`(可空)。 |
| `Conversation.java` | `conversation` | `id`、`title`(非空,100)、`createdAt`、`updatedAt`。`@PrePersist` 同时写 created/updated；`@PreUpdate` 刷新 `updatedAt`（列表按它倒序，不能只靠 `ChatService` 手动 set）。 |
| `Message.java` | `message` | `id`、`conversation`(`@ManyToOne` LAZY, 非空, `@JoinColumn(name="conversation_id")`)、`role`(`@Enumerated(STRING)`, 长度16)、`content`(`columnDefinition = "LONGTEXT"`)、`createdAt`。 |
| `Role.java` | — | 枚举 `USER`、`ASSISTANT`。**加新值（如 SYSTEM）时已存在的库不会自动变更列类型**，需手工 `ALTER TABLE message MODIFY role enum(...)`（`init.sql` 末尾有备注）。 |

**llm/ — 模型调用抽象与实现**

| 文件 | 职责 |
|---|---|
| `LlmClient.java` | 接口，唯一方法 `streamChat(List<LlmMessage> messages, Boolean enableThinking, LlmStreamListener listener)`。**阻塞方法**，调用方应在虚拟线程中执行；正常返回=生成结束，失败抛异常。 |
| `LlmStreamListener.java` | 回调接口：`onToken(String)`（必须实现，正式回答增量）+ `onReasoning(String)`（`default` 空实现，思考增量）。 |
| `LlmMessage.java` | record `(role, content)`，OpenAI 格式。role 由 `Message.role.name().toLowerCase()` 得到（`user`/`assistant`）。 |
| `LlmProperties.java` | `@ConfigurationProperties(prefix="llm")` record：`baseUrl`、`apiKey`、`model`、`maxHistoryMessages`(默认20，<=0 不限)、`enableThinking`(Boolean，可为 null)、`requestTimeoutSeconds`(默认900)。 |
| `OpenAiCompatibleLlmClient.java` | 真实实现。`POST {baseUrl}/chat/completions`，body `{model, stream:true, messages, enable_thinking?}`；头 `Authorization: Bearer <apiKey>`。`enableThinking` 优先取请求级参数，其次 `llm.enable-thinking`，**两者都 null 就不下发该字段**（免得直连不认识它的服务商报 400）。JDK `HttpClient`：`connectTimeout` 10 秒，请求 `timeout` = `requestTimeoutSeconds`（**整轮生成的总上限，不是空闲超时**；到点直接关流，表现为 `IOException("closed")`）。逐行读 SSE：跳过非 `data:` 行、`[DONE]` 结束；内部 record `Chunk(choices)` / `Choice(delta)` / `Delta(@JsonProperty("reasoning_content") reasoningContent, content)`。**同一帧里两个字段都可能有值，按 reasoning → content 顺序回调**。非 200 抛 `IllegalStateException("LLM API 返回 <code>: <body>")`。 |
| `MockLlmClient.java` | 无 key 时的本地假实现，用来先跑通整条流式链路。`CHAR_DELAY_MS = 15`，**按码点切而不是按 char 切**（非 BMP 字符在 UTF-16 占 2 个 char，拆成落单 char 后 Jackson 编不出 UTF-8，SSE 里变成两个 `?`）。思考开关为 true 时先推一段假 `onReasoning`，没 key 也能验证 reasoning 链路。 |

**repository/ — Spring Data JPA**

| 文件 | 方法 |
|---|---|
| `UserRepository.java` | `Optional<User> findByUsername(String)`（登录用，username 有唯一索引）、`List<User> findAllByOrderByIdAsc()`（管理员列表，初始 admin 排第一） |
| `ConversationRepository.java` | `List<Conversation> findAllByOrderByUpdatedAtDesc()`（会话列表） |
| `MessageRepository.java` | `List<Message> findByConversationIdOrderByIdAsc(Long)`、`void deleteByConversationId(Long)`。**后者用 `@Modifying` + JPQL 而非派生 `deleteBy`**：派生删除会先 select 再逐条 delete，长会话删一次就是 2N 条 SQL。**调用方需自带事务**（见 `ConversationService.delete` 的 `@Transactional`）。 |

**service/ — 业务逻辑**

| 文件 | 职责 |
|---|---|
| `ChatService.java` | **全项目最复杂的类**，SSE 流式对话编排。详见第八节 8.1。关键成员：`SSE_TIMEOUT_MARGIN_MS = 30_000`、`DEFAULT_TITLE = "新的对话"`、`TITLE_MAX_LENGTH = 30`、`executor = Executors.newVirtualThreadPerTaskExecutor()`（`@PreDestroy` 里 `shutdownNow()`）、`sseTimeoutMs = requestTimeoutSeconds*1000 + 30000`（构造时算好，只留一个旋钮）。内部类 `SurrogateBuffer`（落单高位代理字符暂存，思考和回答各一个实例）、`StreamAbortedException`（客户端断开/主动停止的信号）。 |
| `ConversationService.java` | 会话 CRUD：`create()`（写死标题「新的对话」）、`list()`、`messages(id)`、`delete(id)`（`@Transactional`，先删消息再删会话）、`require(id)`（找不到抛 404，供 `ChatService` 复用）。 |
| `UserService.java` | `login()`、`me()`、`list()`、`changePassword()`。构造时用 `passwordEncoder.encode(UUID.randomUUID())` 预算一个 `dummyHash`：用户名不存在时也拿它做一次 BCrypt 比对，**防时序攻击**（BCrypt 故意做慢，直接返回会让攻击者靠响应快慢筛出存在的用户名）。「用户不存在」与「密码错」返回同一句 401 文案，不给用户名枚举留口子。 |

**resources / sql / test**

| 文件 | 职责 |
|---|---|
| `resources/application.properties` | 主配置，全部可调项写成 `${环境变量:仓库内默认值}`。逐项说明见第五节。 |
| `resources/application-local.properties` | 本地真实密钥（DB 密码 + `llm.api-key`）。**已被 `.gitignore` 忽略**，靠启动参数 `--spring.profiles.active=local` 生效。 |
| `sql/init.sql` | 建库 + 建 3 张表 + `INSERT IGNORE` 种子管理员（`admin` / BCrypt 哈希 / role=1）。全部 `IF NOT EXISTS`，**可重复执行**。结构与 `ddl-auto=update` 的结果一致；末尾备注了两条手工维护项（`Role` 枚举扩值、`conversation.updated_at` 索引）。 |
| `src/test/.../ChatbotApplicationTests.java` | 只有一个空的 `contextLoads()`，`@SpringBootTest`。**项目没有其他自动化测试**，需要真 MySQL 才能跑起来。 |
| `chatbot/test_sse.py` | 本地手动测试脚本（只用 Python 标准库）。已 gitignore。用法：`python test_sse.py` 跑完整套件（思考转发 + 入库校验 + 思考阶段取消）；`-q "..."` 单问；`-a 4` 4 秒后中断模拟点停止；`-m <会话id>` 查已入库消息；`--cleanup <id...>` 删会话；`--base`/`--user`/`--password` 换环境。脚本里 `IDLE_TIMEOUT_MS = 60_000` 就是它要验证的空闲超时阈值。 |


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
  → ConversationService.messages(id)：require(id) 否则 404 → findByConversationIdOrderByIdAsc
  → List<MessageVO>
```

**③ SSE 流式对话（唯一异步）**
```
POST /api/conversations/{id}/chat   [Accept: text/event-stream]
  → AuthInterceptor（同上，在请求线程里完成）
  → ChatController.chat → ChatService.chat()
       ├─ 请求线程：require(conversationId) → applyAutoTitle → 用户消息入库 → recentHistory(N)
       ├─ 请求线程：new SseEmitter(sseTimeoutMs) + 注册 onTimeout/onError/onCompletion（都置 cancelled）
       ├─ 请求线程：executor.submit(...) 后【立刻 return emitter】
       └─ 虚拟线程：llmClient.streamChat(...) → 逐帧 send(reasoning|delta) → 助手消息入库 → send(done) → complete()
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
| `main.ts` | 5 | `import './assets/main.css'` + `createApp(App).mount('#app')`。没有注册任何插件。 |
| `App.vue` | 小 | **根组件 = 权限闸门**。`restoring` ref 初值 = 「本地是否有 token」；`onMounted` 里若有 token 就调 `fetchMe()` 验证并 `setSession()` 刷新用户信息（角色可能变了），失败则 `clearSession()`。模板三分支：`restoring` → 「正在恢复登录状态…」；`isAuthenticated` → `<ChatView/>`；否则 → `<LoginView/>`。**没有 router，页面切换就是这里换组件。** |
| `auth.ts` | 小 | 登录态。导出 `ROLE_USER=0`/`ROLE_ADMIN=1`（与后端 `Roles` 对齐）、`token`/`currentUser`（模块级 `ref`，初值读 `localStorage` 的 `chatbot.token`/`chatbot.user`）、`isAuthenticated`/`isAdmin`（`computed`）、`setSession(token,user)`、`clearSession()`。`readStoredUser()` 对 JSON 解析失败返回 null（存坏了就当没登录）。**本文件不要 import api.ts**，否则和 `api.ts → auth.ts` 形成循环依赖。 |
| `api.ts` | 中 | 所有后端调用。`BASE = '/api'`。内部 `request<T>(path, init)`：`withAuth()` 挂 `Authorization: Bearer`、非 2xx 时 `extractErrorMessage()` 读 `ErrorResponse.message`、**401 一律 `clearSession()`**（App.vue 随即弹回登录页）、204 返回 `undefined`。导出：`login`、`fetchMe`、`changePassword`、`listUsers`、`createConversation`、`listConversations`、`getMessages`、`deleteConversation`、`streamChat`、类型 `StreamHandlers`。 |
| `types.ts` | 小 | 与后端一一对应：`Conversation`↔`ConversationVO`、`Message`↔`MessageVO`、`CurrentUser`↔`UserVO`、`LoginResult`↔`LoginResponse`、`ChatStreamEvent`↔`ChatEvent`；另有**纯前端**的 `UiMessage`（比 `Message` 多 `id: number\|null`、`reasoning?`、`error?`、`streaming?`）。 |
| `assets/main.css` | 中 | 全局：① `:root` CSS 变量（`--bg`/`--panel`/`--border`/`--text`/`--text-muted`/`--accent`(#10a37f)/`--danger`/`--user-bubble`），`@media (prefers-color-scheme: dark)` 里整套暗色覆盖；② reset（`box-sizing`、`html/body/#app` 高度 100%）；③ **登录页与两个弹窗共用的基础件**：`.field`/`.field-label`/`.field-input`、`.btn-primary`/`.btn-ghost`、`.alert-error`/`.alert-ok`、`.modal-mask`/`.modal-card`(`.wide`)/`.modal-title`/`.modal-actions`。放全局而非 scoped 是因为三处样式完全一样，只写一份。 |
| `views/LoginView.vue` | 中 | 登录表单。`submit()` 成功后**只做 `setSession()`**，不做任何跳转（App.vue 的 computed 自动换根组件）。失败展示后端文案并清空密码框。模板底部有提示「初始管理员 admin / admin」。样式复用全局 `.field*` / `.btn-primary` / `.alert-error`。 |
| `views/ChatView.vue` | **大（约 700 行，前端最大文件）** | 聊天主界面：会话侧边栏 + 顶栏 + 消息区 + 输入条 + 两个弹窗。详见 4.2。 |
| `components/ConversationSidebar.vue` | 中 | 纯展示组件。props `conversations`/`activeId`/`canCreate`；emits `select(id)`/`create()`/`remove(id)`。`formatTime()`：今天显示 `HH:mm`，更早显示 `MM-DD`。删除按钮 hover 才可见，`@click.stop` 防止冒泡触发 select。 |
| `components/MessageBubble.vue` | 小 | 单条消息气泡。prop `message: UiMessage`。① 思考过程用原生 `<details>`/`<summary>` 折叠，`:open="message.streaming \|\| undefined"`（**绑 `undefined` 而不是 `false`**：`open="false"` 这个属性只要存在就生效），summary 文案在「思考中…」/「思考过程」间切换；② 正文 `{{ message.content }}` + `white-space: pre-wrap`，**纯文本，没有 Markdown 渲染**；③ 流式时尾部一个闪烁 `▍` 光标；④ `content` 为空且无 reasoning 时显示「等待模型响应…」；⑤ `message.error` 用 `⚠` 前缀红字展示。 |
| `components/ChangePasswordDialog.vue` | 中 | 改密码弹窗，所有人可见。前端先拦一道（新密码 6~64、两次一致），真正校验以后端为准。成功后 `emit('changed', result)` 把新 `LoginResult` 交回父组件（**必须换上新 token，否则下一个请求就 401**），显示成功提示并 1.2 秒后自动关闭；`closeTimer` 在 `onBeforeUnmount` 里清掉。Esc 关闭、点遮罩关闭。 |
| `components/UserListDialog.vue` | 中 | 用户管理弹窗，仅 `isAdmin` 时 ChatView 才渲染入口。`onMounted` 调 `listUsers()`，表格列 ID/用户名/角色/创建时间，角色用 `.role-tag`（管理员高亮）+ 灰色 `role=N`。403（普通用户误入）和 401（登录态失效）都落到 `error` 展示。Esc / 点遮罩关闭。 |

### 4.2 ChatView.vue 的状态与行为

**状态**

| 变量 | 含义 |
|---|---|
| `conversations` | 会话列表（`listConversations()` 的结果） |
| `activeId` | 当前选中会话 id，`null` = 没选中 |
| `messages: UiMessage[]` | 当前会话的消息（含正在流式生成的临时项） |
| `input` | 输入框内容 |
| `streaming` | 是否正在生成（禁用输入/思考开关，切换「发送」↔「停止」按钮） |
| `loadingMessages` | 历史消息加载中 |
| `thinkingOn` | 思考模式开关，**默认 `true`**，每条消息显式传给后端。想改用服务端默认值就把它设为 `undefined` 传给 api |
| `fatalError` | 全局错误横幅（列表加载失败、删除失败等） |
| `showPasswordDialog` / `showUserDialog` | 两个弹窗开关 |
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
| 流式回调 | `onReasoning` 追加到 `reply.reasoning`、`onDelta` 追加到 `reply.content`、`onDone` 回填 `reply.id`、`onError` 写 `reply.error`；每次都 `scrollToBottom()` | — |
| 收尾 | `finally` 里 `reply.streaming = false`、`streaming = false`、`abortController = null`、`scrollToBottom()`、`void loadConversations()` | 首条消息会触发后端自动起标题，刷新列表才能拿到新标题和新排序 |
| 停止生成 | `stopStreaming()` = `abortController.abort()`，**不调任何后端接口**。`AbortError` 在 catch 里静默处理 | 后端检测连接断开就停止调模型并把已生成部分入库 |
| 键盘 | `onKeydown()`：Enter 发送、Shift+Enter 换行、**`e.isComposing` 时不发送** | 中文输入法候选态的 Enter 不该触发发送 |
| 启动 | `onMounted`：`loadConversations()` → 有会话就选第一个，没有就 `newConversation()` | — |
| 卸载 | `onBeforeUnmount(stopStreaming)` | 退出登录/token 失效被弹回登录页时，必须掐断还在跑的 SSE，否则 fetch 会继续往已不存在的界面写增量，还白烧模型 token |
| 退出登录 | `logout()` = `stopStreaming()` + `clearSession()` | 顺序不能反 |
| 改密码成功 | `onPasswordChanged(result)` = `setSession(result.token, result.user)` | 后端换发了新 token |
| 删除会话 | `removeConversation(id)`：`window.confirm` 二次确认 → 删 → 若删的是当前会话则清空状态 → 刷列表 → 若没选中项就选第一个 | — |

**组件树与数据流**

```
App.vue  （权限闸门：restoring / isAuthenticated）
├── LoginView.vue ──api.login()──→ auth.setSession() ──→ App.vue 自动换成 ChatView
└── ChatView.vue
    ├── ConversationSidebar.vue   props↓ conversations/activeId/canCreate   emits↑ select/create/remove
    ├── MessageBubble.vue × N     props↓ message: UiMessage（无 emit，纯展示）
    ├── ChangePasswordDialog.vue  emits↑ close / changed(LoginResult) → ChatView.setSession()
    └── UserListDialog.vue        emits↑ close（数据自己拉 listUsers()）
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
5. 新增页面：在 `views/` 下加组件，在 `App.vue` 里按条件切换渲染（**没有 router**）。
6. 新增后端调用：统一加在 `api.ts`，类型加在 `types.ts`；**不要在组件里直接写 fetch**，否则丢掉 401 兜底。
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
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/chatbot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` | — | 硬编码，未做环境变量占位 |
| `spring.datasource.username` | `root` | — | 硬编码 |
| `spring.datasource.password` | `123456` | `DB_PASSWORD` | 只是本地兜底，不是真实密钥 |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | — | |
| `spring.jpa.hibernate.ddl-auto` | `update` | — | 自动建表/补列。**只补缺失的表和列，不改动已存在的列** |
| `spring.jpa.show-sql` | `false` | `SHOW_SQL` | 调试 SQL 时 `SHOW_SQL=true` 启动 |
| `spring.jpa.open-in-view` | `false` | — | 关掉 OSIV，避免懒加载在视图层触发 |
| `llm.base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | — | 任何 OpenAI 兼容接口都能直连（改这里 + `llm.model` 即可换服务商） |
| `llm.api-key` | 空 | `LLM_API_KEY` | **留空自动走 `MockLlmClient`** |
| `llm.model` | `qwen3.6-flash` | — | |
| `llm.enable-thinking` | `true` | `LLM_ENABLE_THINKING` | 映射为请求体顶层 `enable_thinking`（百炼/Qwen 系参数）。**服务商不认识该字段时把这一行整行注释掉**，后端就不下发。取舍见 `chatbot/README.md` |
| `llm.request-timeout-seconds` | `900` | `LLM_REQUEST_TIMEOUT` | **整轮生成的总上限，不是空闲超时**。SSE 超时自动取它 +30 秒 |
| `llm.max-history-messages` | `20` | `LLM_MAX_HISTORY` | 每轮只送最近 N 条历史；`<=0` 表示不限制 |

> `LLM_REQUEST_TIMEOUT` 和 `LLM_MAX_HISTORY` 是 properties 里**显式写死**的占位符名，不是 Spring relaxed binding 推出来的 `LLM_REQUEST_TIMEOUT_SECONDS` / `LLM_MAX_HISTORY_MESSAGES`。用错名字不会报错，只会静默走默认值。

`application-local.properties` 当前只有两项（仅本地，不入库）：`spring.datasource.password` 与 `llm.api-key`。**该文件里现在存着一个真实的百炼 API Key**，注意不要复制进任何会提交的文件或对外输出。

### 5.2 前端配置

| 文件 | 内容 |
|---|---|
| `vite.config.ts` | 三件事：插件 `vue()` + `vueDevTools()`；别名 `@` → `./src`；`server.proxy` 把 `/api` 转发到 `http://localhost:8089`（`changeOrigin: true`）。**REST 和 SSE 都走这一条代理**，前端代码里不写死后端地址；后端换端口只改这里 |
| `package.json` | `type: module`；scripts `dev` / `build`(= `run-p type-check build-only`) / `preview` / `build-only` / `type-check`(= `vue-tsc --build`)；运行时依赖**只有 `vue`**；`engines.node = ^22.18.0 \|\| >=24.12.0` |
| `tsconfig.json` | 只有 `references`，指向 `tsconfig.node.json` 与 `tsconfig.app.json`（`files: []`） |
| `tsconfig.app.json` | 继承 `@vue/tsconfig/tsconfig.dom.json`；`include` = `env.d.ts` + `src/**/*`；**`noUncheckedIndexedAccess: true`**；`paths` = `@/*` → `./src/*`；`tsBuildInfoFile` 指到 `node_modules/.tmp/` |
| `tsconfig.node.json` | 继承 `@tsconfig/node24`；只管 `vite.config.*` 等工具文件；`module: preserve`、`moduleResolution: bundler`、`types: [node]`、`noEmit: true` |
| `env.d.ts` | 一行 vite/client 类型引用 |
| `index.html` | `lang="zh-CN"`，标题 `Chatbot`，挂载点 `#app`，入口 `/src/main.ts` |

`.vue` 的类型信息由 `vue-tsc` 提供（`tsc` 认不出来），编辑器侧需要装 Vue (Official) 扩展。

### 5.3 构建与版本控制

| 文件 | 内容 |
|---|---|
| `.gitignore`（根） | 一份覆盖两个子项目。重点条目：`node_modules/`、`dist/`、`target/`、`build/`、`.idea/`、`*.iml`、`.vscode/*`（保留 `extensions.json`）、Eclipse/NetBeans 产物、`chatbot/.mvn/wrapper/maven-wrapper.jar`、`*.log`、`.eslintcache`、`__screenshots__/`、`HELP.md`、`*.local`、**`application-local.properties`**（第 72 行）、`.env`、**`chatbot/test_sse.py`**、**`AGENTS.md`（第 78 行）** |
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

**`conversation`（会话）** — 实体 `entity/Conversation.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | |
| `title` | `varchar(100)` | NOT NULL | 创建时写死「新的对话」；首条消息后 `ChatService.applyAutoTitle()` 自动改成消息前 30 字（超出加 `…`） |
| `created_at` | `datetime(6)` | NOT NULL | |
| `updated_at` | `datetime(6)` | NOT NULL | **会话列表按它倒序**。`@PreUpdate` 自动刷新 + `ChatService.saveMessage()` 手动 set |

> **没有 `user_id` 列**：会话不归属任何用户，所有登录用户共享同一份列表。见第十三节。
> 数据量大后可自行加 `CREATE INDEX idx_conversation_updated_at ON conversation (updated_at);`（Hibernate 不会建，`init.sql` 末尾有备注）。

**`message`（消息）** — 实体 `entity/Message.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | 历史消息按它升序取 |
| `conversation_id` | `bigint` | NOT NULL, FK → `conversation(id)`，索引名 `FK6yskk3hxw5sklwgi25y6d5u1l` | `@ManyToOne` LAZY |
| `role` | `enum('ASSISTANT','USER')` | NOT NULL | `@Enumerated(EnumType.STRING)`，Hibernate 按**字母序**生成枚举值 |
| `content` | `longtext` | NOT NULL | `columnDefinition = "LONGTEXT"` |
| `created_at` | `datetime(6)` | NOT NULL | |

> 思考过程（reasoning）**不入库**，只有 `delta` 拼出来的正式回答会写进 `content`。刷新页面看不到思考过程，这是设计而非 bug。

### 6.2 初始化与结构同步

两条等价路径，任选其一：

1. 执行 `chatbot/sql/init.sql`：建库（`utf8mb4` / `utf8mb4_unicode_ci`）+ 建 3 张表 + `INSERT IGNORE` 种子管理员 `admin`（`admin` 的 BCrypt cost=10 哈希，role=1）。全部 `IF NOT EXISTS` / `INSERT IGNORE`，**脚本可重复执行**，也不会把改过的密码覆盖回 `admin`。
2. 直接启动后端：`ddl-auto=update` 自动建表，`AdminUserInitializer` 在 `sys_user` 为空时自动建默认管理员。

**同步规则**：表结构的唯一事实源是 `entity/` 下的三个实体类。改实体后必须同步 `sql/init.sql`——`ddl-auto=update` 只补新表新列，**不会修改或删除已有列**，两边不一致时脚本会悄悄过期。


---

## 七、API 接口汇总

### 7.1 全量接口表（共 9 个）

| # | 方法 | 路径 | 鉴权 | 成功状态码 | 请求体 | 响应体 | 后端入口 |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/api/auth/login` | **无（唯一白名单）** | 200 | `LoginRequest` | `LoginResponse` | `AuthController.login` |
| 2 | GET | `/api/auth/me` | 需要 | 200 | — | `UserVO` | `AuthController.me` |
| 3 | POST | `/api/conversations` | 需要 | **201** | — | `ConversationVO` | `ConversationController.create` |
| 4 | GET | `/api/conversations` | 需要 | 200 | — | `ConversationVO[]`（`updated_at` 倒序） | `ConversationController.list` |
| 5 | GET | `/api/conversations/{id}/messages` | 需要 | 200 | — | `MessageVO[]`（按 `id` 升序） | `ConversationController.messages` |
| 6 | DELETE | `/api/conversations/{id}` | 需要 | **204**（无响应体） | — | — | `ConversationController.delete` |
| 7 | POST | `/api/conversations/{id}/chat` | 需要 | 200 + `text/event-stream` | `ChatRequest` | SSE 事件流 | `ChatController.chat` |
| 8 | PUT | `/api/users/me/password` | 需要 | 200 | `ChangePasswordRequest` | `LoginResponse`（**新 token**） | `UserController.changePassword` |
| 9 | GET | `/api/users` | **`@RequireAdmin`** | 200 | — | `UserVO[]`（按 `id` 升序） | `UserController.list` |

前端封装位置：全部在 `chatbot-web/src/api.ts`，一一对应 `login` / `fetchMe` / `createConversation` / `listConversations` / `getMessages` / `deleteConversation` / `streamChat` / `changePassword` / `listUsers`。

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

// UserVO —— 没有 password 字段
{ "id": 1, "username": "admin", "role": 1, "roleLabel": "管理员", "createdAt": "..." }

// ConversationVO
{ "id": 12, "title": "新的对话", "createdAt": "...", "updatedAt": "..." }

// MessageVO —— role 是小写字符串
{ "id": 34, "role": "user", "content": "你好", "createdAt": "..." }
{ "id": 35, "role": "assistant", "content": "…", "createdAt": "..." }

// ChatRequest
{ "message": "你好", "enableThinking": true }   // enableThinking 可省略；省略 = 用服务端 llm.enable-thinking

// ChangePasswordRequest
{ "oldPassword": "admin", "newPassword": "new-secret" }

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
| 400 | `@Valid` 校验失败（`message` 为空、新密码长度不符）、请求体不是合法 JSON、原密码不正确、新密码与原密码相同 |
| 401 | 未带 token、token 格式错/签名错/已过期、账号已被删、改过密码导致旧 token 失效。**前端收到任何 401 都 `clearSession()` 弹回登录页** |
| 403 | 普通用户访问 `@RequireAdmin` 接口（`GET /api/users`） |
| 404 | 会话不存在、用户不存在 |
| 204 | 删除会话成功 |

### 7.3 SSE 事件契约（`POST /api/conversations/{id}/chat`）

由 `dto/ChatEvent.java` 产出，`@JsonInclude(NON_NULL)` 让 null 字段不出现在 JSON 里。每帧形如 `data:{...}` + 空行。

| `type` | 形状 | 语义 |
|---|---|---|
| `reasoning` | `{"type":"reasoning","content":"..."}` | 思考过程增量，出现在正式回答之前。仅推理模型且思考开关为 true 时出现。**只给前端展示，后端不入库** |
| `delta` | `{"type":"delta","content":"..."}` | 正式回答的增量文本，逐段推送。前端累加即为完整回答，也是唯一入库的内容 |
| `done` | `{"type":"done","messageId":123}` | 生成结束，助手消息已入库，`messageId` 用于前端回填 `UiMessage.id` |
| `error` | `{"type":"error","content":"..."}` | 出错。**文案在 `content`，不是 `message` 字段** |

约定：前端遇到不认识的 `type` **直接忽略**（`api.ts` 的 `switch` 没有 default 分支），保持向后兼容；新增事件类型时老前端不会崩。

为什么必须转发思考、停止生成的语义、SSE 超时如何推导，见 `chatbot/README.md`。

### 7.4 手动测试

`chatbot/README.md` 的「手动测试」一节有 4 条 `curl.exe` 命令（登录换 token → 建会话 → SSE 对话 → 404/400 错误体）。注意：**拦截器跑在 `@Valid` 和控制器之前**，所以不带 token 时四条全部返回 401，想看到 404 和 400 必须先带 token。

更完整的验证用 `chatbot/test_sse.py`（见 2.2）。

---

## 八、核心流程

### 8.1 聊天流程（`ChatService`）

```
【请求线程】
POST /api/conversations/{id}/chat  {message, enableThinking?}
  → AuthInterceptor 校验通过
  → ChatService.chat(conversationId, request)
      1. conversationService.require(id)            // 不存在直接 404
      2. applyAutoTitle(conversation, message)      // 标题仍为「新的对话」时，取消息前 30 字（超出加 …）
                                                    //   只改内存字段，第 3 步的 save() 会一并落库
      3. saveMessage(conversation, USER, message)   // 用户消息入库 + 刷新 conversation.updated_at
      4. recentHistory(id)                          // 全量查后 subList 取最近 N 条（llm.max-history-messages）
                                                    //   map 成 LlmMessage(role 小写, content)
      5. new SseEmitter(sseTimeoutMs)               // = requestTimeoutSeconds*1000 + 30000
         onTimeout / onError / onCompletion 都只做一件事：cancelled.set(true)
      6. executor.submit(() -> stream(...))         // 虚拟线程
      7. return emitter                             // 请求线程到此结束

【虚拟线程】stream()
  两个 SurrogateBuffer（reasoning / content 各一个）+ 一个 StringBuilder full + AtomicLong reasoningChars
  try {
    llmClient.streamChat(history, enableThinking, listener)
      listener.onReasoning(t): checkCancelled → reasoningChars += len → buffer.feed(t) → send(reasoning)
      listener.onToken(t):     checkCancelled → full.append(t)     → buffer.feed(t) → send(delta)
    saveMessage(conversation, ASSISTANT, full)      // 只存回答，不存思考
    send(done(messageId)); emitter.complete()
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
浏览器加载 → main.ts → App.vue
  restoring = (localStorage 里有 token)
  onMounted:
    无 token → restoring = false → 渲染 LoginView
    有 token → GET /api/auth/me
                200 → setSession(token, me)（顺带刷新用户信息，角色可能变了）→ 渲染 ChatView
                401 → api.ts 已 clearSession()，这里再兜一次 → 渲染 LoginView
  ChatView.onMounted → loadConversations() → 有会话选第一个，没有就 newConversation()
```

运行期任何接口返回 401 → `api.ts` 的 `clearSession()` → `isAuthenticated` 变 false → `App.vue` 自动弹回登录页。**因此不存在绕过登录能访问的页面，也不需要路由守卫。**

### 8.5 空会话回收

```
newConversation():
  canCreateConversation 为假（当前会话还没消息）→ 直接 return，不建新会话
  列表里已有 title == "新的对话" 的会话 → selectConversation 跳过去，不建第二个
  否则 createConversation() → loadConversations() → activeId = null（强制重拉）→ selectConversation(newId)

selectConversation(id):
  记下 leavingId / leavingWasEmpty → 切换 → 拉历史 → 成功后若 leavingWasEmpty 则 discardEmptyConversation(leavingId)

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


---

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

**手动验证 SSE**（后端已启动，仓库根目录）

```powershell
python chatbot/test_sse.py                 # 完整套件：思考转发 + 入库校验 + 思考阶段取消
python chatbot/test_sse.py -q "你好"        # 单问，打印帧时间线
python chatbot/test_sse.py -q "..." -a 4    # 4 秒后中断，模拟点「停止」
python chatbot/test_sse.py -m 41            # 查看 41 号会话已入库的消息
```

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
| 上述任何一项 | **刷新本文文末「最后更新」日期，并在第十四节变更记录追加一行** |

同步时请保持本文风格：表格化、写「为什么」、标注刻意的设计取舍，不要写成流水账。

---

## 十二、扩展点

1. **换 LLM 服务商**：改 `llm.base-url` + `llm.model` + `llm.api-key` 即可，任何 OpenAI 兼容接口都能直连。服务商不认识 `enable_thinking` 时，把 `application.properties` 里那一行整行注释掉（`LlmProperties.enableThinking` 变 null → 请求体不下发该字段）。要支持非兼容协议，新写一个 `LlmClient` 实现并在 `LlmConfig` 里加分支。
2. **新增角色 / 细粒度权限**：在 `Roles` 加常量，新增注解（如 `@RequireOperator`），在 `AuthInterceptor.requiresAdmin()` 旁边加对应判断。`sys_user.role` 是 int，**不用改列类型**。
3. **新增接口**：`controller/` 下加方法即可，默认要登录；管理员接口加 `@RequireAdmin`。需要当前用户就在形参里声明 `CurrentUser`。
4. **新增前端页面**：`views/` 下加组件，在 `App.vue` 里按条件切换渲染（没有 router；若页面数量增长到需要路由，引入 `vue-router` 时要把 `App.vue` 的权限闸门改成路由守卫）。
5. **消息分页**：`MessageRepository` 换成 `Pageable` 版本，`ConversationService.messages()` 与 `api.ts` 的 `getMessages()` 同步改；前端加「加载更多」。
6. **会话归属用户**：见 13.1 第 1 条，这是当前最大的功能缺口。
7. **`chatbot/README.md` 末尾的「后续待加」**：会话重命名接口、重新生成、token 用量统计、消息分页。

---

## 十三、已知限制、坑与文档偏差

### 13.1 功能限制

1. **会话不按用户隔离（最重要）**：`conversation` 表没有 `user_id`，`ConversationController` / `ChatController` 也不接 `CurrentUser`。**所有登录用户看到、并能读写同一份会话列表**，`GET /api/conversations/{id}/messages` 和 `DELETE /api/conversations/{id}` 都不校验归属。目前「多用户」只体现在鉴权与用户管理上，不是数据隔离。要做隔离需要：加列 + 改 3 个查询 + 两个控制器接 `CurrentUser` + service 层校验归属。
2. **无分页**：会话列表、消息列表、用户列表都是全量查询。长会话会把整段历史查出来再 `subList` 截断（`ChatService.recentHistory`），数据量大时是明显的性能点。
3. **无文件上传 / 多模态**：只支持纯文本对话。
4. **无重新生成、无会话重命名、无 token 用量统计**（后端 README 的「后续待加」）。
5. **SSE 单向**：客户端断开后无法恢复，只能重新发起请求；已生成部分会入库，刷新能看到。
6. **思考过程不持久化**：`reasoning` 只推给前端展示，刷新页面即消失。
7. **无自动化测试**：`src/test` 只有空的 `contextLoads()`，且需要真 MySQL；`test_sse.py` 是手动脚本且已 gitignore，**新克隆的仓库里没有它**。
8. **无 Markdown 渲染**：回答里的代码块、列表、表格都按纯文本 + `pre-wrap` 显示。
9. **`LlmClient` Bean 启动时定型**：运行期改 `llm.api-key` 不会从 Mock 切到真实客户端，必须重启。
10. **CORS 全开**：`allowedOriginPatterns("*")`，仅适合内网/开发；对外部署要收紧。
11. **`spring.datasource.url` / `username` 硬编码**在 properties 里，没有环境变量占位，换环境要改文件。

### 13.2 容易踩的坑

1. **`AGENTS.md` 被 gitignore**（`.gitignore` 第 78 行）：它只在本地工作区存在，不会随仓库分发。若希望协作规则对所有克隆者生效，需要把它从 `.gitignore` 移除并提交。
2. **`chatbot/test_sse.py` 同样被 gitignore**：新环境里没有这个脚本。
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

### 13.3 文档偏差记录（2026-09-21 已全部修正）

首次全量通读时发现三份 README 有 4 处与代码不符，现已按实际情况改写。**这张表留着是为了说明「为什么这几处的措辞是现在这样」**，不要把它们又改回原来的说法。

| # | 位置 | 原说法（错） | 已改为 |
|---|---|---|---|
| 1 | `chatbot-web/README.md`「SSE 流式解析」末句 | `MessageBubble.vue` 负责 **Markdown 渲染**、思考折叠和打字效果 | 明确写「正文是纯文本（`{{ message.content }}` + `white-space: pre-wrap`），项目没有引入任何 Markdown 渲染库」，并指出要加 Markdown 得先引依赖再同步本文 4.1 与 13.1 |
| 2 | `chatbot-web/README.md`「目录结构」 | `MessageBubble.vue  消息气泡，区分 user / assistant` | 补上「纯文本渲染（无 Markdown）+ 思考折叠 + 打字光标」 |
| 3 | `chatbot/README.md`「错误响应体」正文 **和** 配置表 | 由 `GlobalExceptionHandler` + **`server.error.include-message=always`** 保证（配置表里还列了这一项） | 改为「由 `GlobalExceptionHandler` 自己组装 `ErrorResponse`，**不依赖** `server.error.include-message`」，并**删掉了配置表里那一行**——`application.properties` 里从来没有这项配置 |
| 4 | 根 `README.md`、`chatbot/README.md`、`chatbot-web/README.md` | 「multi-user auth」/「多用户」容易被读成「每个用户有自己的会话」 | 三份 README 各加一处显式说明：多用户只指鉴权与用户管理，会话全站共享、可互相读写删除，并指向本文 13.1 |

> 顺带在根 `README.md` 的 Documentation Map 里补了一节 **For AI agents**，指向 `AGENTS.md`，并说明它被 gitignore、新克隆的仓库里看不到时应直接以本文为准。

---

## 十四、变更记录

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 初版：架构、模块、数据库、API、安全、扩展点、已知限制 |
| 2026-09-21 | 全量重写为「接手即用」的事实源：新增五分钟速览与检索入口速查、逐文件清单、Bean 装配表、三类请求调用链、前后端编码约定、SSE 解析要点、ChatView 状态与行为表、列级表结构说明、API 请求/响应 JSON 示例与状态码语义、五条核心流程时序、第十一节文档同步清单、18 条踩坑记录、3 条文档偏差。同时更正：会话不按用户隔离、`MessageBubble` 无 Markdown 渲染、`AGENTS.md` 与 `test_sse.py` 被 gitignore |
| 2026-09-21（第二次） | 修正三份 README 共 4 处与代码不符的表述（`MessageBubble` 的 Markdown 渲染、`server.error.include-message`、会话不按用户隔离），并把 13.3 从「待修正的偏差」改写为「已修正记录」；根 `README.md` 增加 For AI agents 指引与更准确的文档地图 |

---

*最后更新：2026-09-21*

