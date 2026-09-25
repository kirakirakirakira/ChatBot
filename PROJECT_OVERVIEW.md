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
| 默认账号 | `admin` / `admin`，**超级管理员**（`sql/init.sql` 的 `INSERT IGNORE` 与 `SeedUserInitializer` 二选一兜底创建；种子必须是超管，理由见 8.6）。另有**每个角色一个测试账号**：`test_super` / `test_admin` / `test_user` / `test_guest`，密码统一 `test123456`（`auth.seed-test-users=false` 或 `AUTH_SEED_TEST_USERS=false` 关掉），层级权限「谁能管谁」只有四个不同角色的账号同时在库里才点得出来 |

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
| 改个人资料 / 个人信息页 | 后端 `controller/UserController.java` + `service/UserService.java` + `dto/UpdateProfileRequest.java`；前端 `views/ProfileView.vue` + `api.ts` 的 `updateMyProfile()` / `fetchMyStats()` / `revokeMySessions()` |
| 改管理台批量操作 | 后端 `service/AdminUserBatchService.java`（逐条事务的编排）+ `dto/BatchUser*.java`；前端 `views/admin/UsersView.vue` 的勾选与批量条 + `components/admin/BatchResetPasswordDialog.vue` / `BatchResultDialog.vue` |
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
│       │   ├── auth/           # 8 个文件：token 签发校验 + 拦截器 + 当前用户注入 + 角色 + 账号状态
│       │   ├── config/         # 6 个文件：Web/CORS、BCrypt、LLM Bean、种子账号（超管自愈 + 测试账号）、全局异常
│       │   ├── controller/     # 8 个文件：Auth / Conversation / Chat / User / Llm / Attachment / AdminUser / AdminAudit
│       │   ├── dto/            # 29 个文件：请求体、响应 VO、SSE 事件、统一错误体、管理端 PageVO/AdminUserVO/AdminAuditLogVO、批量 BatchUser*、个人资料 UpdateProfileRequest/UserProfileStatsVO 等
│       │   ├── entity/         # 7 个文件：User / Conversation / Message / Attachment / Role / AdminAuditLog / AuditAction
│       │   ├── llm/            # 8 个文件：客户端抽象 + OpenAI 兼容实现 + Mock + 配置 + 调用选项 + 多模态 content 段
│       │   ├── repository/     # 6 个文件：Spring Data JPA 接口 + UserSpecifications（Criteria 查询条件）
│       │   └── service/        # 7 个文件：Chat / Conversation / User / Attachment / AdminUser / AdminAudit / AdminUserBatch 七个 Service
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
        ├── api/                # client.ts：传输层（全站唯一一份）；userAdmin.ts：用户管理模块接口
        ├── auth.ts             # 登录态（模块级 ref + localStorage）
        ├── types.ts            # 与后端 DTO/VO 一一对应的类型
        ├── assets/main.css     # 全局 CSS 变量 + 登录/弹窗共用件
        ├── lib/                # markdown.ts：markdown-it + highlight.js + DOMPurify
        ├── views/              # LoginView、ChatView、ProfileView（个人信息，懒加载）、admin/UsersView（用户管理，懒加载）、ForbiddenView、NotFoundView
        └── components/         # ConversationSidebar、MessageBubble、MarkdownContent、AttachmentThumb、ChangePasswordDialog、SystemPromptDialog、UserMenu、ModuleNav、common/ConfirmDialog、admin/（含批量重置密码 / 批量结果两个弹窗）、icons/（含 IconUser）
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
| `AuthInterceptor.java` | `HandlerInterceptor.preHandle`。① 非 `HandlerMethod`（CORS 预检、静态资源、404）直接放行；② 只认 `Authorization: Bearer <token>`，**不做 `?token=` 兜底**（会进访问日志）；③ `TokenService.verify()` 校签名与有效期；④ **回表查一次用户**（`userRepository.findById`），让删号/改角色/改密码/禁用立刻生效；⑤ `UserStatus.isEnabled(user.status)` 为假 → 401「账号已被禁用」（**禁用因此立刻生效，不用等 token 过期**）；⑥ 比较 `payload.iat()` 与 `user.passwordChangedAt`（都转 epoch 毫秒），旧 token 作废；⑦ `@RequireAdmin` 检查（方法级或类级注解）；⑧ 把 `CurrentUser` 放进 request attribute，key = 常量 `CURRENT_USER_ATTRIBUTE = "chatbot.currentUser"`。401 与 403 都抛 `ResponseStatusException`。 |
| `AuthProperties.java` | `@ConfigurationProperties(prefix = "auth")` 的 record：`tokenSecret`、`tokenTtlHours`(默认12)、`defaultAdminUsername`(默认 admin)、`defaultAdminPassword`(默认 admin)、`seedTestUsers`(默认 true)、`testUserPassword`(默认 test123456)。由 `AuthConfig` 上的 `@EnableConfigurationProperties` 启用。后两项只服务于 `SeedUserInitializer` 的测试账号补齐。 |
| `CurrentUser.java` | record `(Long id, String username, Integer role)` + `isAdmin()`。**刻意不用 ThreadLocal**：`/chat` 是 SSE 异步接口，请求线程与生成线程不是同一个。 |
| `CurrentUserArgumentResolver.java` | `HandlerMethodArgumentResolver`，让控制器方法直接声明 `CurrentUser` 形参。从 request attribute 取；取不到抛 401（兜底，正常走不到）。 |
| `RequireAdmin.java` | 注解，`@Target({TYPE, METHOD})` + `RUNTIME`。权限判断统一在 `AuthInterceptor`，不在业务层重复。 |
| `Roles.java` | 角色常量 + **层级模型**：`USER=0`、`ADMIN=1`、`SUPER_ADMIN=2`、`GUEST=3`（访客：应用内权限同普通用户，只是层级最低，作「降权但不禁用」的承接位）；`rank(Integer)` 给管理层级（超管 3 > 管理员 2 > 普通用户 1 > 访客 0，**与 code 解耦**），`canAccessAdmin()` 是 `@RequireAdmin` 的判定（管理员或超管），`knownByRankDesc()` / `isKnown()` / `label()`。`sys_user.role` 存 **int 而非字符串/ENUM**，加角色不用改列类型。 |
| `UserStatus.java` | 账号状态常量：`ENABLED = 0`、`DISABLED = 1`；`isEnabled(Integer)`（null 当启用，只是防御性兜底）、`label(Integer)`。与 `Roles` 同一口径：存 int 不改列类型，以后加「锁定 / 待激活」不用迁移。2026-09-25 加入。 |

**config/ — 装配与全局行为**

| 文件 | 职责 |
|---|---|
| `WebConfig.java` | `WebMvcConfigurer`。① CORS：`/api/**`，白名单来自 `CorsProperties`（默认只有 `http://localhost:5173`，**刻意不用 `*`**），方法 GET/POST/PUT/DELETE/OPTIONS，头 `*`；② 拦截器：`addPathPatterns("/api/**").excludePathPatterns(PUBLIC_PATHS)`，**`PUBLIC_PATHS` 只有 `/api/auth/login` 一项**（白名单模式：新接口默认要登录）；③ 注册 `CurrentUserArgumentResolver`。改免登录路径就改这个文件的常量。 |
| `AuthConfig.java` | `@EnableConfigurationProperties(AuthProperties.class)` + 声明 `PasswordEncoder` Bean = `BCryptPasswordEncoder()`（默认 strength 10）。因为没引 starter-security，这个 Bean 必须自己声明。 |
| `CorsProperties.java` | `cors.*` 配置 record。`origins()` 去掉空白项后返回白名单数组；**全空白抛 `IllegalStateException` 让启动失败**，不退化成「全开」——CORS 配漏了的默认结果不该是 `*` |
| `LlmConfig.java` | `@EnableConfigurationProperties(LlmProperties.class)` + 声明 `LlmClient` Bean：`apiKey` 为空/空白 → `MockLlmClient`，否则 → `OpenAiCompatibleLlmClient`。**Bean 在启动时定型，改 key 必须重启。** |
| `SeedUserInitializer.java` | `ApplicationRunner`（2026-09-25 由 `AdminUserInitializer` 改名并扩职责）。三件**幂等**的事：① `sys_user` 为空时建初始**超级管理员**（不跑 `init.sql`、直接靠 `ddl-auto=update` 启动的人不至于谁都登不进去）；② **超管自愈**：系统里一个启用的超管都没有时，把种子账号（`auth.default-admin-username`）提回超管——层级规则下没有超管就是再也管不动的死局，role=1 的老库靠这一步自动升级；只要还有别的启用超管，就尊重管理员对种子账号的降级，不会每次重启改回去；③ 按 `auth.seed-test-users` 补齐每个角色一个测试账号（只在用户名不存在时建：不覆盖已有账号、不重置改过的密码、不改被调整过的角色）。日志**故意不打印任何密码**。 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice`。处理 `ResponseStatusException`（保留状态码，reason 进 message）、`MethodArgumentNotValidException`（拼字段错误）、`MaxUploadSizeExceededException`（413）、`MissingServletRequestPartException`、`HttpMessageNotReadableException`、**`DataIntegrityViolationException`（400「数据冲突」：兜建号竞态，文案不泄露表名约束名）**。刻意不加 catch-all、每个 handler 显式设 `Content-Type`（理由见 13.2 第 7、8 条）。 |

**controller/ — REST 入口（薄，只做参数绑定与委派）**

| 文件 | 前缀 | 端点 |
|---|---|---|
| `AuthController.java` | `/api/auth` | `POST /login`（`@Valid LoginRequest` → `LoginResponse`；全站唯一免登录接口）、`GET /me`（`CurrentUser` → `UserVO`） |
| `ConversationController.java` | `/api/conversations` | `POST`（→ 201 + `ConversationVO`）、`GET`（→ `List<ConversationVO>`）、`GET /{id}/messages?before=&limit=`（→ `MessagePageVO`，items 里带附件元信息）、`PUT /{id}/title`（→ `ConversationVO`）、`DELETE /{id}`（→ 204）、`POST /{id}/attachments`（multipart 字段名 `file` → 201 + `AttachmentVO`）。`ChatController` 另有 `POST /{id}/regenerate`（SSE，请求体可省略） |**每个方法都声明 `CurrentUser` 形参**，归属校验在 service 层 |
| `AttachmentController.java` | `/api/attachments` | `GET /{id}` → 图片字节（`Content-Type` = 存的 mime + `X-Content-Type-Options: nosniff`）。**没有类级 `@RequestMapping`**：只有一个方法、且路径不在 `/api/conversations` 下 |
| `ChatController.java` | `/api/conversations` | `POST /{id}/chat`，`produces = TEXT_EVENT_STREAM_VALUE`，`@Valid ChatRequest` + `CurrentUser` → `SseEmitter`。**CurrentUser 必须在进 service 之前解析**：生成跑在虚拟线程上，那里拿不到 request attribute，补不了归属校验 |
| `LlmController.java` | `/api/llm` | `GET /options`（→ `LlmOptionsVO`）。默认受保护：模型清单不敏感，但没必要在未登录时暴露部署用了哪些模型 |
| `UserController.java` | `/api/users` 只剩「管自己」：`PUT /me/password`（→ 新 `LoginResponse`）、`PUT /me/system-prompt`（→ `UserVO`，**不换发 token**）、`PUT /me/profile`（昵称/邮箱/手机号 → `UserVO`，同样不换发 token）、`GET /me/stats`（自己的会话/消息/附件计数）、`POST /me/revoke`（204，作废自己**全部**登录态、含当前这个）。「管别人」的全部在 `AdminUserController`。原来的 `GET`（管理员列表）已删：唯一调用方是只读弹窗，被 `/admin/users` 页面取代。 |

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
| `UserVO.java` | `(id, username, nickname, role, roleLabel, status, statusLabel, email, phone, systemPrompt, lastLoginAt, createdAt, mustChangePassword)`。**没有 password 字段**：BCrypt 哈希也不能出网。`roleLabel` / `statusLabel` 由后端 `Roles.label()` / `UserStatus.label()` 给出，前端不维护映射。`mustChangePassword` 是给「刚认证的这个会话」的指令（登录与 `/me` 都带）。nickname / email / phone / status / lastLoginAt 是 2026-09-25 为「个人信息」页加的：一屏展示完自己的账号情况，不值得为几个字段再开一个接口。 |
| `AdminUserVO.java` 等 9 个管理端 DTO | `AdminUserVO`（管理表格一行：含 `status` / `statusLabel` / `lastLoginAt` / `mustChangePassword` / **`canManage`（当前操作者能不能管这一行，前端据此锁控件）**，**不含** `systemPrompt`）、`PageVO<T>`（offset 分页壳：`items/page/size/total/totalPages`，`of(Page, mapper)`）、`UserAdminOptionsVO`（角色/状态字典：`roles` 是**可指派**的角色，按操作者层级过滤；`allRoles` 是全量角色，只给列表筛选器用）、`CreateUserRequest` / `UpdateRoleRequest` / `UpdateStatusRequest` / `ResetPasswordRequest`（**刻意不加 bean validation 的条件字段**：`newPassword` 是否必填取决于 `generate`，注解表达不了，校验在 service）/ `ResetPasswordResult` / `AdminAuditLogVO`（审计行，`actionLabel` 中文由 `AuditAction.label()` 给）、`UpdateProfileRequest`（昵称/邮箱/手机号，全空白 = 清空；**刻意不含 username 与 role**）、`UserProfileStatsVO`（三个计数）、批量三件套 `BatchUserAction`（枚举，请求体用 String 接再 `of()` 解析，为的是中文 400）/ `BatchUserRequest`（`ids` 1~100 + 一个动作 + 可选 role/newPassword/generate）/ `BatchUserResultVO`（逐条结果：`requested/succeeded/failed/items`，item 带失败原因与一次性随机密码）。 |
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
| `User.java` | `sys_user` | 表名用 `sys_user` 而不是 `user`（MySQL 关键字）。字段 `id`、`username`(唯一)、`password`(BCrypt)、`role`、`createdAt`、`passwordChangedAt`(可空)、`systemPrompt`(可空 TEXT：该用户的人设，每轮作为 system 消息放在历史最前面)、`nickname`/`email`/`phone`(均可空，个人信息页维护；nickname 只做展示、不参与登录与审计快照) |
| `Conversation.java` | `conversation` | `id`、`owner`（`@ManyToOne` LAZY 非空，`@JoinColumn(name="owner_id")`，外键名 `fk_conversation_owner`）、`title`(非空,100)、`createdAt`、`updatedAt`。表上 `@Index idx_conversation_owner_updated(owner_id, updated_at)` 服务「按用户查列表 + updated_at 倒序」这一条查询。取单个会话只走 `ConversationRepository.findByIdAndOwnerId`，**直接用 findById 就是越权** |
| `Message.java` | `message` | `id`、`conversation`(`@ManyToOne` LAZY, 非空)、`role`(`@Enumerated(STRING)`, 长度16)、`content`(LONGTEXT)、`reasoning`(可空 LONGTEXT，思考全文)、`model`(varchar 64，回答用的模型)、`promptTokens` / `completionTokens` / `reasoningTokens`(可空 int，用量；拿不到就 NULL，不填 0 冒充真实值)、`createdAt` |
| `Attachment.java` | `attachment` | 图片附件：`id`、`conversation`(`@ManyToOne` LAZY 非空，外键 `fk_attachment_conversation`，**RESTRICT**)、`messageId`(可空 Long，**刻意不做 `@ManyToOne`**：附件先于消息存在；NULL = 传了还没发出去的孤儿)、`mime`(白名单 5 种)、`fileName`、`sizeBytes`(列名带 `_bytes`：JPQL 里 `size` 是保留函数名)、`data`(`LONGBLOB`)、`createdAt`。一个附件只属于一条消息，不变式由 `AttachmentRepository.linkToMessage` 的 `message_id IS NULL` 条件保证 |
| `AdminAuditLog.java` | `admin_audit_log` | 管理端操作审计：`actor_id/actor_name`、`action`（字符串，取值见 `AuditAction`）、`target_id/target_name`、`detail`、`created_at`。**actor/target 都不建外键**（删号是功能，留痕不该挡住删人），靠名字快照认人；只记成功的写操作且与业务同事务。 |
| `AuditAction.java` | — | 审计动作名常量（CREATE_USER / UPDATE_ROLE / UPDATE_STATUS / RESET_PASSWORD / REVOKE_SESSIONS / DELETE_USER）+ `label()` 中文名。**存 varchar 不用 ENUM**：加动作零迁移，不用 ALTER 枚举列；不认识的老动作名 `label()` 原样返回。 |
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
| `AdminAuditLogRepository.java` | 只有 `JpaRepository`，**没有 update / delete 方法是有意的**：审计表一旦能改就失去留痕意义，清历史走运维 SQL 不留应用入口。 |
| `UserRepository.java` | `Optional<User> findByUsername(String)`（登录用，username 有唯一索引）、`countByRoleAndStatus(Integer, Integer)`（「最后一个启用的管理员」一道闸）、继承 `JpaSpecificationExecutor<User>` 供管理端筛选分页。`findAllByOrderByIdAsc()` 已随 `GET /api/users` 删除。 |
| `ConversationRepository.java` | `List<Conversation> findAllByOwnerIdOrderByUpdatedAtDesc(Long)`（会话列表，走复合索引）、`Optional<Conversation> findByIdAndOwnerId(Long, Long)`（**唯一的单会话查询入口**，带归属条件）、`int updateTitle(id, ownerId, title)`（**`@Modifying` JPQL 只改 title**：save() 会触发 `@PreUpdate` 刷新 updated_at 把会话顶到列表最前面，而改名不是「活动」；where 带 owner_id，越权改名影响行数为 0） 、`deleteByOwnerId(Long)`（删号级联，`@Modifying` 批量删）、`countByOwnerId(Long)`（个人信息页统计，走复合索引） |
| `MessageRepository.java` | `findByConversationIdOrderByIdAsc(Long)`（只留给「不限条数」场景）、`findByConversationId(Long, Pageable)` 与 `findByConversationIdAndIdLessThan(Long, Long, Pageable)`（**id 游标分页**，排序刻意放 Pageable 里而不是方法名上）、`void deleteByConversationId(Long)`。**删除用 `@Modifying` + JPQL 而非派生 `deleteBy`**：派生删除会先 select 再逐条 delete，长会话删一次就是 2N 条 SQL。**调用方需自带事务**（见 `ConversationService.delete` 的 `@Transactional`） 、`deleteByOwnerId(Long)`（删号级联）、`countByOwnerId(Long)`（`@Query` 子查询：message 没有 owner 列，归属靠 conversation 传递） |
| `AttachmentRepository.java` | `findLinkableIds(ids, conversationId)`（**只查 id 不查实体**：校验阶段把 LONGBLOB 拉进内存是白费）、`findByMessageIdInOrderByIdAsc(ids)`（拼多模态历史，带字节）、`findMetaByMessageIdIn(ids)`（**JPQL 构造器投影**成 `Object[]{messageId, AttachmentVO}`，消息列表只要元信息）、`findByIdForOwner(id, ownerId)`（读字节前的归属校验）、`linkToMessage(ids, messageId, conversationId)`（`@Modifying`，带 `message_id IS NULL` 条件保证一个附件只挂一条消息）、`deleteByConversationId(id)` 、`deleteByOwnerId(Long)`（删号级联第一步）、`countByOwnerId(Long)`（只 count 不取行：这张表带 LONGBLOB） |

**service/ — 业务逻辑**

| 文件 | 职责 |
|---|---|
| `ChatService.java` | **全项目最复杂的类**，SSE 流式对话编排。详见第八节 8.1。关键成员：`SSE_TIMEOUT_MARGIN_MS = 30_000`、`DEFAULT_TITLE = "新的对话"`、`TITLE_MAX_LENGTH = 30`、`MAX_THINKING_BUDGET = 262144`（qwen3.8 系最大思维链长度）、`executor = Executors.newVirtualThreadPerTaskExecutor()`、`sseTimeoutMs = requestTimeoutSeconds*1000 + 30000`。`buildOptions()` 统一做模型白名单 + 思考预算校验（思考关着时预算丢弃）；`recentHistory()` 把存库思考随历史回传（preserve_thinking）。内部类 `SurrogateBuffer`、`StreamAbortedException` |
| `ConversationService.java` | 会话 CRUD，**每个公开方法都接 `CurrentUser`**：`create(user)`、`list(user)`、`messages(id, user, before, limit)`（游标分页）、`delete(id, user)`、`requireOwned(id, user)`（404 口径）、`rename(id, user, request)`、`locateRegenerateTarget(id)`（只定位不删除，返回 `DroppedReply(prompt, model, assistantMessageId)`；最后一条不是助手消息就 400）+ `deleteAssistantMessage(id)`（校验都过了再删：模型不支持图片时必须 400 在删除之前，否则用户连旧回答都丢了） |
| `AttachmentService.java` | 图片附件的上传 / 校验 / 挂载 / 读取。`ALLOWED_MIME` 5 种位图（**刻意不含 SVG**：能带脚本）、`MAX_BYTES = 5MB`、`MAX_PER_MESSAGE = 4`。`upload()` 先传后发（上传不等发送）；`validateForConversation()` 一次挡掉「不存在 / 是别人的 / 已被占用」三种情况（都 400）；`requireOwned()` 读字节前查归属（404）。**只依赖 `ConversationService`，不被它反向依赖**，否则构造器循环 |
| `UserService.java` | `login()`（`@Transactional`：`touchLastLogin()` 是 `@Modifying`；先验密码再判禁用 → 403）、`me()`、`changePassword()`、`updateSystemPrompt()`、`updateProfile()`（全空白存 NULL；**不换发 token**：改资料不是安全事件）、`stats()`（三个 count，只读事务）、`revokeOwnSessions()`（推 `passwordChangedAt`，本人所有 token 含当前这个立刻失效）。目标 id 一律只来自 token。构造时预算 `dummyHash` 防时序攻击。原来的 `list()` 已删。 |
| `AdminAuditService.java` | 审计的写与读：`record(actor, action, targetId, targetName, detail)` 供各管理 Service 在**自己的事务里**调用；`page(page, size)` 给 `/api/admin/audit`（id 倒序）。独立成服务是因为审计表全管理端共用，不绑死用户管理一个模块。 |
| `AdminUserService.java` | 管理端增删改查，**业务规则与自我保护全在这里**（控制器薄）：筛选分页（`UserSpecifications`）、建号查重、改角色/启停/重置密码/强制下线/删号级联；「不能对自己下手」「最后一个启用的管理员动不得」一律 400。批量操作**不重写任何一条规则**：`AdminUserBatchService` 逐条穿代理调这里的方法。 |
| `AdminUserBatchService.java` | 管理台批量编排：`execute(actor, BatchUserRequest)` → `BatchUserResultVO`（逐条结果）。**自身不带 `@Transactional`**：逐条穿代理调 `AdminUserService` 的单条方法 = 逐条独立事务，勾选里混进一个不能动的人不会连累其余 19 个；层级 / 自我保护 / 审计全部复用单条实现。只把 `ResponseStatusException` 与 `DataIntegrityViolationException` 翻译成逐条失败，别的异常照旧 500（真 bug 不该被伪装成「这一条失败了」）。**单独成 Bean 而不是在 AdminUserService 里加 batch 方法**：同类内 `this.xxx()` 不走代理，`@Transactional` 会静默失效。 |
| `AdminAuditController.java` | `GET /api/admin/audit`（类级 `@RequireAdmin`）。挂在 `/api/admin/audit` 而不是 `/api/admin/users/audit`：审计表是全管理端共用的，读取入口不绑死单个模块。 |
| `AdminUserController.java` | `/api/admin/users` 九个接口（八个单条 + `POST /batch` 批量），**类级 `@RequireAdmin`**（这个控制器下不存在普通用户该能调的方法，漏打一个就是越权洞）。批量只是把单条动作在一批 id 上跑一遍，编排与逐条事务在 `AdminUserBatchService`。 |
| `UserSpecifications.java` | 用户列表的 Criteria 条件：关键字 / 角色 / 状态「传了才拼」；LIKE 转义 `% _ \\`（用户输入不当通配符）；`Locale.ROOT` 小写（避免土耳其语 I 问题）。 |

**resources / sql / test**

| 文件 | 职责 |
|---|---|
| `resources/application.properties` | 主配置，全部可调项写成 `${环境变量:仓库内默认值}`。逐项说明见第五节。 |
| `resources/application-local.properties` | 本地真实密钥（DB 密码 + `llm.api-key`）。**已被 `.gitignore` 忽略**，靠启动参数 `--spring.profiles.active=local` 生效。 |
| `sql/init.sql` | 建库 + 建 4 张表（含 `attachment`）+ `INSERT IGNORE` 种子**超级管理员**（`admin` / BCrypt 哈希 / role=2）+ 四个测试账号（role 2/1/0/3，密码统一 test123456 的哈希）。全部 `IF NOT EXISTS`，**可重复执行**。结构与 `ddl-auto=update` 的结果一致；「已有库升级」段按日期记录每次表结构迁移的 SQL（2026-09-25 新增「个人资料三列」与「测试账号 + 种子自愈」两段）；末尾备注了两条手工维护项（`Role` 枚举扩值、删号级联顺序为什么留在应用层）。 |
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
| `SeedUserInitializer` | `@Component` + `ApplicationRunner` | 启动完成后跑一次：建超管 / 超管自愈 / 补测试账号，三件都幂等 |
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
| `layouts/AppShell.vue` | 小 | 应用外壳：左侧 `ModuleNav` + 内容区 `<RouterView/>`，另外挂**关不掉的强制改密框**（`currentUser.mustChangePassword` 为真时以 force 模式渲染 `ChangePasswordDialog`）——强制改密是账号级的事，不跟模块走。登录页刻意不在外壳里。`.app-main` 的 `min-width: 0` 不能省。 |
| `components/ModuleNav.vue` | 小 | 56px 模块导航条：条目**从路由表派生**（`meta.moduleId` + `order`），`requiresAdmin` 的模块对普通用户不渲染；底部账号按钮弹出**账号菜单**（个人信息 / 用户管理 / 操作记录 / 退出登录，管理页没有聊天顶栏的头像菜单，这些是外壳级的事）。激活态用 vue-router 自带的 `router-link-active`。**菜单必须贴导航条右侧弹出**（`left: calc(100% + 10px)`）：导航条只有 56px 宽，居中定位会让菜单左半截跑出视口被裁掉（2026-09-25 修的就是这个）。 |
| `components/icons/IconChat.vue` 等 | 小 | 模块导航图标：24×24 描边、`stroke=currentColor`，颜色由导航条 CSS 决定。新模块照这个规格加一个 SFC，经 `markRaw()` 放进路由 meta（不 markRaw 会被 reactive 代理，白白增加开销）。现有 `IconChat`（聊天）、`IconUser`（个人信息，单人像）、`IconUsers`（用户管理，多人像）。 |
| `views/ForbiddenView.vue` | 小 | `/403`：已登录但权限不够。显示当前角色 + 「回到聊天」。挂在外壳里，所以还能用导航条去别的模块，不至于困在死页上。 |
| `views/NotFoundView.vue` | 小 | `/:pathMatch(.*)*` 兜底，同样挂在外壳里：登录状态下打错地址还能点导航回去，不用手改 URL。 |
| `auth.ts` | 小 | 登录态。导出 `ROLE_USER=0`/`ROLE_ADMIN=1`/`ROLE_SUPER_ADMIN=2`/`ROLE_GUEST=3`（与后端 `Roles` 对齐）；`isAdmin` 是**管理端准入**（管理员或超管，同后端 `canAccessAdmin`），另有 `isSuperAdmin`、`token`/`currentUser`（模块级 `ref`，初值读 `localStorage` 的 `chatbot.token`/`chatbot.user`）、`isAuthenticated`/`isAdmin`（`computed`）、`setSession(token,user)`、`clearSession()`，另有 `displayName`（昵称优先、回退登录名：顶栏头像、左下角菜单、个人信息页三处共用，避免某处忘了回退显示空白名字）。`readStoredUser()` 对 JSON 解析失败返回 null（存坏了就当没登录）。**本文件不要 import api.ts**，否则和 `api.ts → auth.ts` 形成循环依赖。 |
| `api/client.ts` | 小 | **全站唯一的 HTTP 传输层**（2026-09-25 从 `api.ts` 抽出）。导出 `API_BASE='/api'`、`request<T>(path, init)`、`withAuth(headers?)`、`extractErrorMessage(response)`。`request()` 的口径：挂 `Authorization: Bearer`、非 2xx 读 `ErrorResponse.message`、**401 一律 `clearSession()`**（界面随即弹回登录页）、204 返回 `undefined`。抽出来的理由：项目要长出一批和聊天**平级的功能模块**，每个模块一个 api 文件互不干扰，但「挂鉴权头 + 401 清登录态」只能有一份实现，否则某个模块自己写 fetch 忘了处理 401，界面就会停在一张点什么都 401 的死页面上。**依赖方向刻意单向 `client.ts → auth.ts`**：本文件不 import router——反向 import 会形成 `router → guards → api → client → router` 的环，ESM 下表现为某个绑定初始化时还是 `undefined`，很难查。「掉登录态就回登录页」的兜底放在 `App.vue`，因为那条路径不止 401 一个入口（还有主动退出登录），兜底要兜在一个口子上。 |
| `api/userAdmin.ts` | 小 | 用户管理模块的后端调用：`listAdminUsers` / `fetchUserAdminOptions` / `createAdminUser` / `updateUserRole` / `updateUserStatus` / `resetAdminUserPassword` / `revokeAdminUserSessions` / `deleteAdminUser` / `batchAdminUsers`（批量，返回逐条结果）/ `listAdminAudit`。传输层仍只用 `api/client.ts` 的 `request()`。 |
| `api.ts` | 中 | **聊天与登录的接口清单**：只描述「有哪些接口、请求体和响应长什么样」，传输层在 `api/client.ts`。新增别的平级模块另开 `src/api/<模块>.ts`（见 `api/userAdmin.ts`）。导出：`login`、`fetchMe`、`changePassword`、`updateSystemPrompt`、`updateMyProfile` / `fetchMyStats` / `revokeMySessions`（个人信息页三件套）、`createConversation`、`listConversations`、`getMessages`、`deleteConversation`、`renameConversation`、`fetchLlmOptions`、`uploadAttachment`、`fetchAttachmentUrl`、`streamChat` / `streamRegenerate`（共用 `consumeSse()`）、类型 `StreamHandlers` / `StreamOptions` / `MessageUsage` / `MessagePageQuery`。 |
| `types.ts` | 小 | 与后端一一对应：`Conversation`↔`ConversationVO`、`Message`↔`MessageVO`、`CurrentUser`↔`UserVO`（含 `mustChangePassword?`）、`LoginResult`↔`LoginResponse`、`ChatStreamEvent`↔`ChatEvent`、`MessagePage`↔`MessagePageVO`、`LlmOptions`↔`LlmOptionsVO`、`Attachment`↔`AttachmentVO`；管理端 `AdminUser`↔`AdminUserVO`、`Page<T>`↔`PageVO`、`UserAdminOptions`、`ResetPasswordResult`、批量 `BatchUserAction` / `BatchUserBody` / `BatchUserItem` / `BatchUserResult`；个人信息 `MyStats`↔`UserProfileStatsVO`、`UpdateProfileBody`；另有纯前端的 `UiMessage` / `AttachmentRef`。`CurrentUser` 的 nickname / email / phone / status / lastLoginAt 都是**可选**：localStorage 里升级前存下的旧登录态没有这些字段。 |
| `assets/main.css` | 中 | 全局设计变量（暖中性纸感浅色 / 深墨暗色，跟随系统 `prefers-color-scheme`）+ 半径 / 阴影 / 细滚动条 / 选区 / 焦点环 + **登录与四个弹窗共用的基础件**（`.field*` / `.btn-*` / `.alert-*` / `.modal-*`，放全局是因为长得一样、只写一份） |
| `lib/markdown.ts` | 小 | markdown-it 实例 + 自定义 fence 渲染器（代码块包 `.code-block`、加语言标签和复制按钮）+ `renderMarkdown()`（渲染后过 DOMPurify）。**安全两道锁**：`html:false` 转义输入里的原始 HTML，DOMPurify 再兜一道；`breaks:true` 让单换行也换行。高亮只注册 highlight.js common 子集，认不出的语言原样输出不报错 |
| `views/LoginView.vue` | 中 | 登录表单：两团淡品牌色光晕背景 + 居中卡片（圆角 20、入场动画）。成功后 `setSession()` + `router.replace(safeNextPath(next) ?? HOME_PATH)`——用 replace 而不是 push：登录页不该留在历史记录里，否则登录后按后退又回到登录页。失败展示后端文案并清空密码框。 |
| `views/ChatView.vue` | **大（前端最大文件）** | 聊天主界面：隐形顶栏（会话标题 + `UserMenu` 头像菜单，「用户管理」项 `router.push('/admin/users')`）、居中限宽 760px 的消息列、空状态问候语 + 开场建议 chips、胶囊合成输入框（自动长高、圆形发送/停止、待发送图片缩略图条 + 粘贴/拖拽传图）。详见 4.2 |
| `components/ConversationSidebar.vue` | 中 | 纯展示组件。props `conversations`/`activeId`/`canCreate`；emits `select(id)`/`create()`/`remove(id)`/`rename(id, title)`。品牌标 + 虚线「新建对话」；列表项 hover 才浮出重命名/删除图标（盖住时间戳，一行宽度有限）；双击标题或点铅笔行内改名 |
| `components/MessageBubble.vue` | 小 | 单条消息。**助手消息带头像、不套气泡**（长回答铺在背景上比塞进盒子里好读）；用户消息是浅色圆角 pill。图片缩略图排在文字之前（与发给模型的顺序一致）、思考折叠、Markdown 正文、用量行、hover 才显形的「重新生成」 |
| `components/AttachmentThumb.vue` | 小 | 一张图的缩略图。`url` 有值就直接用（本地预览，**不 revoke**——所有权归创建方 ChatView）；只有 `id` 时 `onMounted` 里 `fetchAttachmentUrl()` 取字节转 objectURL、`onBeforeUnmount` 里自己 revoke。点击新标签打开原图；取失败显示「加载失败」而不是空白 |
| `components/ChangePasswordDialog.vue` | 中 | 改密码弹窗。`force` prop：管理员重置过密码时由 AppShell 以**关不掉**的形态弹出（无取消、Esc/遮罩无效），普通入口不传、行为不变。成功后 `emit('changed', result)`，调用方必须 `setSession()` 换上新 token。 |
| `components/SystemPromptDialog.vue` | 中 | 系统提示词（人设）弹窗，所有人可见。打开时用本地登录态里的 `currentUser.systemPrompt` 回显（不为回显再请求一次 /me）；保存调 `PUT /users/me/system-prompt`，成功后 `emit('changed', user)` 让父组件 `setSession(token, user)` 覆盖本地用户信息——**不用重新登录**。清空保存 = 移除人设 |
| `components/MarkdownContent.vue` | 小 | Markdown 渲染容器：`v-html` 挂 `renderMarkdown()` 的结果；**复制按钮是渲染产物、不在 Vue 事件体系里**，靠容器上的委托监听 + `closest('.copy-btn')` 处理，剪贴板写失败会显示「复制失败」而不是假装成功；`streaming` 为 true 时用 CSS `::after` 在最后一个块末尾挂闪烁光标 |
| `components/common/ConfirmDialog.vue` | 小 | 通用二次确认：`title/text/confirmLabel/danger/busy` + `requireText`（输入一致才点亮确认，删号用它挡手滑）。危险操作不再用 `window.confirm` 系统框。 |
| `components/admin/UserFormDialog.vue` | 小 | 新建用户：用户名 / 初始密码（可一键生成，`crypto.getRandomValues` + 去易混字符）/ 角色 / 状态；选项来自 `/options` 字典。 |
| `components/admin/ResetPasswordDialog.vue` | 小 | 重置密码：生成随机 / 手输两种模式；生成模式下明文**只展示这一次**（readonly + 复制按钮 + 醒目提示），关掉就再也看不到。 |
| `views/admin/AuditView.vue` | 小 | 操作记录页（`/admin/audit`，`meta.hidden` 不进导航条，从 UsersView 的「操作记录」链接进入）：时间 / 操作者 / 动作 / 目标 / 明细 + 分页。**只读且没有「清空记录」按钮**：能删的审计不叫审计。 |
| `views/admin/UsersView.vue` | 中 | 用户管理页（`/admin/users`）：表格（行内改角色、启停、重置密码、强制下线、删除）+ 搜索/角色/状态筛选 + offset 分页 + **多选与批量操作条**（启用 / 禁用 / 改角色 / 重置密码 / 强制下线 / 删除，只勾当前页、翻页即清空）；行内操作失败整页重拉。角色**筛选器**吃 `options.allRoles`（全量），行内改角色与新建弹窗吃 `options.roles`（可指派）。**层级锁死**：`canManage=false` 或自己的行，复选框与角色下拉、操作按钮全部 `disabled` + tooltip 说明原因（「不能修改自己的角色与信息」/「只能管理层级低于自己的用户」），不做「看起来能点、点了才 400」。批量结果用 `BatchResultDialog` 逐条摊开（含失败原因与一次性随机密码）。 |
| `components/UserMenu.vue` | 小 | 顶栏头像下拉：个人信息 / 用户管理（仅 admin）/ 系统提示词 / 修改密码 / 退出登录。点外部或 Esc 关闭。**顶栏只留一个头像按钮**：文字按钮并排会把顶栏变成工具条。头像首字母与菜单名取 `auth.ts` 的 `displayName`（昵称优先） |
| `views/ProfileView.vue` | 中 | 个人信息页（`/profile`，每个登录用户可进，懒加载）：基本资料表单（昵称 / 邮箱 / 手机号，登录名**只读**）+ 账号信息（角色 / 状态 / 创建 / 上次登录）+ 使用统计（会话 / 消息 / 图片）+ 安全区（改人设 / 改密码 / 退出所有设备，后两者复用聊天页那两个弹窗组件）。保存资料后 `setSession(token, user)` 覆盖本地登录态（后端不换发 token） |
| `components/admin/BatchResetPasswordDialog.vue` | 小 | 批量重置密码：每人一个随机密码 / 统一设成一个密码两种模式；界面上直接写明「统一 = 所有人共用一个凭据」，引导用随机模式 |
| `components/admin/BatchResultDialog.vue` | 小 | 批量结果窗：成功 / 失败逐条列出（失败带后端中文原因）；随机密码逐条展示 + 「复制全部」。批量是逐条独立提交，「成功 3 / 失败 1」是正常结果而不是报错，所以不能用一个 toast 代替 |

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
    ├── ModuleNav.vue          条目从 routes.ts 的 meta.moduleId 派生；底部账号按钮 + 退出登录
    ├── ChangePasswordDialog.vue（force）  mustChangePassword=true 时关不掉
    └── <RouterView/>
        ├── ChatView.vue
        │   ├── ConversationSidebar.vue   props↓ conversations/activeId/canCreate   emits↑ select/create/remove
        │   ├── MessageBubble.vue × N     props↓ message: UiMessage（无 emit，纯展示）
        │   │       └── AttachmentThumb.vue × N   props↓ attachment: AttachmentRef（自己 fetch 字节、自己 revoke）
        │   ├── ChangePasswordDialog.vue  emits↑ close / changed(LoginResult) → ChatView.setSession()
        │   └──（「用户管理」菜单项 → router.push('/admin/users')）
        ├── views/admin/UsersView.vue   /admin/users（懒加载，独立分包）
        ├── views/admin/AuditView.vue   /admin/audit（操作记录，hidden 路由）
        │   ├── admin/UserFormDialog.vue / admin/ResetPasswordDialog.vue
        │   └── common/ConfirmDialog.vue（删号要求输入用户名）
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
| `auth.seed-test-users` | `true` | `AUTH_SEED_TEST_USERS` | 是否补齐每个角色一个测试账号（`test_super` / `test_admin` / `test_user` / `test_guest`）。只在用户名不存在时创建，不覆盖已有账号。**生产环境关掉** |
| `auth.test-user-password` | `test123456` | `AUTH_TEST_USER_PASSWORD` | 测试账号的统一密码，入库前 BCrypt。长度不在 6~64 内时 `SeedUserInitializer` **启动直接抛异常**，不静默跳过 |
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
| `role` | `int` | NOT NULL | `0`=普通用户，`1`=管理员，`2`=超级管理员，`3`=访客。常量与**层级**在 `auth/Roles.java`（`rank()`：超管 3 > 管理员 2 > 普通用户 1 > 访客 0）；管理操作只允许「上对下」 |
| `status` | `int` | NOT NULL DEFAULT 0 | `0`=启用，`1`=禁用。常量在 `auth/UserStatus.java`。禁用后**登录 403、已登录的下一个请求 401**（AuthInterceptor 每请求回表）。**列带 DEFAULT 是刻意的**：给有数据的旧库加 NOT NULL 列时，没有 DEFAULT 在 MySQL 严格模式下直接失败；有 DEFAULT 则老行填 0=启用，不会把任何人锁在门外。2026-09-25 加入 |
| `created_at` | `datetime(6)` | NOT NULL | `@PrePersist` 写入，`updatable=false` |
| `password_changed_at` | `datetime(6)` | NULL | 从没改过密码为 NULL。签发时间（token `iat`）早于它的登录态一律作废 → **改密码会踢掉其他所有设备的会话**。**这列必须保持 `datetime(6)`**：后端按毫秒比较，精度掉到秒会让改密码那一秒签发的旧 token 躲过失效判断 |
| `last_login_at` | `datetime(6)` | NULL | 最近一次登录成功的时间。`UserService.login()` 用 `UserRepository.touchLastLogin()`（`@Modifying` 单列 update）写入：不 load 实体、不碰其它列，所以 `login()` 是 `@Transactional`。从没登录过为 NULL，不填假时间冒充真实值。2026-09-25 加入 |
| `must_change_password` | `tinyint(1)` | NOT NULL DEFAULT 0 | 管理员重置过密码、本人还没改。经 `UserVO.mustChangePassword` 在登录响应和 `/me` 里带出，前端据此强制弹改密框（放 `/me` 是为了刷新页面后还能再触发）；本人改密成功后清 0。2026-09-25 加入（重置密码接口本身在 T5） |
| `system_prompt` | `text` | NULL | 该用户的系统提示词（人设）；NULL = 没设，后端不下发 system 消息。2026-09-24 加入 |
| `nickname` | `varchar(50)` | NULL | 昵称（展示名），个人信息页维护；NULL = 没设，界面回退显示 username。**只做展示**：不参与登录、不进审计快照，所以改昵称不会断掉「谁干的」这条线索。2026-09-25 加入 |
| `email` | `varchar(100)` | NULL | 联系邮箱；格式校验只在应用层（`UpdateProfileRequest` 的 `@Email`），列上不建约束。2026-09-25 加入 |
| `phone` | `varchar(30)` | NULL | 联系电话；**刻意不做格式校验**（区号 / 分机 / 国际号写法太多，写死正则只会逼人填假的），只卡长度。2026-09-25 加入 |

**`conversation`（会话）** — 实体 `entity/Conversation.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | |
| `title` | `varchar(100)` | NOT NULL | 创建时写死「新的对话」；首条消息后 `ChatService.applyAutoTitle()` 自动改成消息前 30 字（超出加 `…`） |
| `owner_id` | `bigint` | NOT NULL, FK `fk_conversation_owner` → `sys_user(id)`；复合索引 `idx_conversation_owner_updated(owner_id, updated_at)` | 会话归属用户。列表 / 读取 / 删除 / 发消息全部带它过滤；**查不到（不存在或属于别人）一律 404**，不返回 403 |
| `created_at` | `datetime(6)` | NOT NULL | |
| `updated_at` | `datetime(6)` | NOT NULL | **会话列表按它倒序**。`@PreUpdate` 自动刷新 + `ChatService.saveMessage()` 手动 set |

> `owner_id` 是 2026-09-24 加的（之前会话全站共享，是当时最大的功能缺口，见第十四节变更记录）。**管理员也没有跨用户特权**：`/api/admin/users` 管的是账号本身，会话接口对管理员同样只返回他自己的。
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

**`admin_audit_log`（管理端操作审计）** — 实体 `entity/AdminAuditLog.java`

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `bigint` | PK, AUTO_INCREMENT | |
| `actor_id` | `bigint` | NOT NULL，**无外键** | 操作者 id。不建外键：删号是功能，管理员自己也可能被删，留痕不该反过来挡住删人 |
| `actor_name` | `varchar(50)` | NOT NULL | 操作者用户名**快照**：账号被删后这是唯一线索 |
| `action` | `varchar(32)` | NOT NULL | 动作名，取值见 `entity/AuditAction`。存字符串不用 ENUM：加动作零迁移 |
| `target_id` | `bigint` | NOT NULL，**无外键** | 目标用户 id；DELETE_USER 之后目标行已不存在，留 id 做线索 |
| `target_name` | `varchar(50)` | NOT NULL | 目标用户名快照，理由同 `actor_name` |
| `detail` | `varchar(255)` | NULL | 变更明细（`role 0 → 1`、`生成随机密码，强制下次改密`…），给人看的不做解析 |
| `created_at` | `datetime(6)` | NOT NULL | `@PrePersist` 写入；索引 `idx_audit_created` 留给以后的按时间范围查询 |

> 审计**只记成功的写操作**，且与业务操作同一个事务：业务回滚则审计一起回滚，不留「操作失败了却有一条留痕」的假记录。
> 新表用 `CREATE TABLE IF NOT EXISTS`，全新库与旧库都靠 `init.sql` 这一段，**不需要 ALTER**（`ddl-auto=update` 也会自建）。

### 6.2 初始化与结构同步

两条等价路径，任选其一：

1. 执行 `chatbot/sql/init.sql`：建库（`utf8mb4` / `utf8mb4_unicode_ci`）+ 建 3 张表 + `INSERT IGNORE` 种子管理员 `admin`（`admin` 的 BCrypt cost=10 哈希，role=1）。全部 `IF NOT EXISTS` / `INSERT IGNORE`，**脚本可重复执行**，也不会把改过的密码覆盖回 `admin`。
2. 直接启动后端：`ddl-auto=update` 自动建表，`AdminUserInitializer` 在 `sys_user` 为空时自动建默认管理员。

**同步规则**：表结构的唯一事实源是 `entity/` 下的三个实体类。改实体后必须同步 `sql/init.sql`——`ddl-auto=update` 只补新表新列，**不会修改或删除已有列**，两边不一致时脚本会悄悄过期。 另外 `init.sql` 末尾有一段「已有库升级」注释（2026-09-24 会话归属那次留下的迁移 SQL）：给**已经有数据的旧库**手工执行 ALTER 用。以后再做破坏性结构变更，请往那段下面追加，别只改 CREATE TABLE——`IF NOT EXISTS` 对旧库是空操作。


---

## 七、API 接口汇总

### 7.1 全量接口表（共 27 个）

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
| 9 | PUT | `/api/users/me/system-prompt` | 需要 | 200 | `UpdateSystemPromptRequest` | `UserVO`（更新后，含自己的 systemPrompt） | `UserController.updateSystemPrompt` |
| 10 | PUT | `/api/conversations/{id}/title` | 需要 | 200 | `RenameConversationRequest` | `ConversationVO`（更新后的那一条） | `ConversationController.rename` |
| 11 | POST | `/api/conversations/{id}/regenerate` | 需要 | 200 + `text/event-stream` | `RegenerateRequest`（**整个体可省略**；`model` 不传沿用旧回答的模型） | SSE 事件流（与 7 号接口同一套） | `ChatController.regenerate` |
| 12 | GET | `/api/llm/options` | 需要 | 200 | — | `LlmOptionsVO`（可选模型清单 + 服务端默认模型 + **visionModels**） | `LlmController.options` |
| 13 | POST | `/api/conversations/{id}/attachments` | 需要 | **201** | multipart，字段名 `file`（单张 ≤5MB、MIME 白名单 5 种、每会话可多次传） | `AttachmentVO` | `ConversationController.uploadAttachment` |
| 14 | GET | `/api/attachments/{id}` | 需要 | 200 | — | 图片字节（`Content-Type` = 存的 mime，`nosniff`） | `AttachmentController.get` |
| 15 | GET | `/api/admin/users` | **类级 `@RequireAdmin`** | 200 | —（query：`keyword` / `role` / `status` / `page`(0 起) / `size`(默认 20、上限 100)） | `PageVO<AdminUserVO>`（按 `id` 升序） | `AdminUserController.list` |
| 16 | GET | `/api/admin/users/options` | **类级 `@RequireAdmin`** | 200 | — | `UserAdminOptionsVO`（角色 / 状态字典，中文 label 后端给；`roles`=可指派、`allRoles`=全量供筛选） | `AdminUserController.options` |
| 17 | POST | `/api/admin/users` | **类级 `@RequireAdmin`** | **201** | `CreateUserRequest` | `AdminUserVO` | `AdminUserController.create` |
| 18 | PUT | `/api/admin/users/{id}/role` | **类级 `@RequireAdmin`** | 200 | `UpdateRoleRequest` | `AdminUserVO`（立即生效：拦截器每请求回表） | `AdminUserController.updateRole` |
| 19 | PUT | `/api/admin/users/{id}/status` | **类级 `@RequireAdmin`** | 200 | `UpdateStatusRequest` | `AdminUserVO`（禁用立即生效） | `AdminUserController.updateStatus` |
| 20 | POST | `/api/admin/users/{id}/password` | **类级 `@RequireAdmin`** | 200 | `ResetPasswordRequest`（`generate` 与 `newPassword` 二选一） | `ResetPasswordResult`（随机密码**只回显这一次**） | `AdminUserController.resetPassword` |
| 21 | POST | `/api/admin/users/{id}/revoke` | **类级 `@RequireAdmin`** | **204** | — | — | `AdminUserController.revoke` |
| 22 | DELETE | `/api/admin/users/{id}` | **类级 `@RequireAdmin`** | **204** | — | — | `AdminUserController.delete` |
| 23 | GET | `/api/admin/audit` | **类级 `@RequireAdmin`** | 200 | —（query：`page`(0 起) / `size`(默认 20、上限 100)） | `PageVO<AdminAuditLogVO>`（id 倒序） | `AdminAuditController.list` |
| 24 | PUT | `/api/users/me/profile` | 需要 | 200 | `UpdateProfileRequest`（昵称 / 邮箱 / 手机号，全空白 = 清空） | `UserVO`（更新后；**不换发 token**） | `UserController.updateProfile` |
| 25 | GET | `/api/users/me/stats` | 需要 | 200 | — | `UserProfileStatsVO`（会话 / 消息 / 附件计数） | `UserController.stats` |
| 26 | POST | `/api/users/me/revoke` | 需要 | **204** | — | —（本人全部登录态含当前这个立刻失效，调用方必须自己清本地登录态） | `UserController.revokeOwnSessions` |
| 27 | POST | `/api/admin/users/batch` | **类级 `@RequireAdmin`** | 200 | `BatchUserRequest`（`ids` 1~100 + 一个动作 + 可选 `role` / `newPassword` / `generate`） | `BatchUserResultVO`（**逐条结果**，不是总数） | `AdminUserController.batch` |


> 3~7 号接口的控制器方法都声明了 `CurrentUser` 形参（解析见 `CurrentUserArgumentResolver`），归属校验统一在 `ConversationService` / `ChatService` 里做：**查不到或不是自己的会话一律 404**，管理员也没有跨用户特权。15~22 号与 27 号的 `CurrentUser` 不为鉴权（类级注解已经拦了），为的是 `AdminUserService` 的自我保护规则（见 8.6）。24~26 号是「管自己」，目标 id 只来自 token。
前端封装位置：1~14 号与 24~26 号在 `chatbot-web/src/api.ts`，15~23 号与 27 号在 `chatbot-web/src/api/userAdmin.ts`（平级模块各开一个 api 文件，传输层共用 `api/client.ts`）。
> 13 号超大小限制返回 **413**（不是 400）：异常在 multipart 解析阶段抛出，到不了 `AttachmentService` 里那道给中文文案的校验，所以由 `GlobalExceptionHandler` 的 `MaxUploadSizeExceededException` 处理器接住。14 号**不能用 `<img src>` 直接引用**：img 带不了 `Authorization` 头，而项目刻意不做 `?token=` 兜底。
> 15~22 号与 27 号的 400 文案全是中文且面向操作者：分页越界、未知角色/状态、用户名已存在、数据冲突、自我保护规则（「不能删除自己」「系统至少需要保留一个启用的管理员」…），见 8.6。27 号的**逐条失败不走 400**：能落到某一行上的规则违反（层级不够、是自己、最后一个管理员）都进 `items[i].message`，只有整批级的错（超过 100 个、未知动作、密码太短）才 400。

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
{ "id": 1, "username": "admin", "role": 1, "roleLabel": "管理员", "systemPrompt": "你是资深 DBA…", "createdAt": "...", "mustChangePassword": false }
// mustChangePassword=true = 管理员重置过密码、本人还没改，前端据此强制弹改密框；登录与 /me 都带

// AdminUserVO —— 管理表格的一行；没有 password，也没有 systemPrompt（「能列用户」不等于「能看别人的人设」）
{ "id": 2, "username": "alice", "role": 0, "roleLabel": "普通用户", "status": 1, "statusLabel": "禁用",
  "lastLoginAt": "2026-09-25T11:39:44", "mustChangePassword": true, "createdAt": "...", "canManage": true }
// canManage = 目标层级严格低于当前操作者（Roles.rank 比较）；false 时前端把这一行的控件全部锁死

// PageVO<AdminUserVO> —— GET /api/admin/users。offset 分页（page 从 0 起），和消息的游标分页刻意不是一套
{ "items": [ ... ], "page": 0, "size": 20, "total": 2, "totalPages": 1 }

// UserAdminOptionsVO —— GET /api/admin/users/options。下拉选项用它生成，前端不写死 code
// roles = **可指派**的角色，只含层级低于当前操作者的（超级管理员看到 3 个，管理员看到 2 个；下拉里根本没有的选项比「选了才说不行」干净）
// allRoles = 全量角色（层级从高到低），只给列表的角色**筛选器**用：筛选是读操作，不受指派层级限制
// 下面是超级管理员看到的形状
{ "roles": [ { "code": 1, "label": "管理员" }, { "code": 0, "label": "普通用户" }, { "code": 3, "label": "访客" } ],
  "allRoles": [ { "code": 2, "label": "超级管理员" }, { "code": 1, "label": "管理员" }, { "code": 0, "label": "普通用户" }, { "code": 3, "label": "访客" } ],
  "statuses": [ { "code": 0, "label": "启用" }, { "code": 1, "label": "禁用" } ] }

// CreateUserRequest / UpdateRoleRequest / UpdateStatusRequest（role、status 可省略 = 默认 0）
{ "username": "alice", "password": "alice-123456", "role": 0, "status": 0 }
{ "role": 1 }
{ "status": 1 }

// ResetPasswordRequest / ResetPasswordResult
{ "generate": true }                 // 或 { "newPassword": "xxxxxx" }（6~64）
{ "generatedPassword": "CgR7U8MwjMDEGjbC", "mustChangePassword": true }   // 手输模式 generatedPassword 为 null

// AdminAuditLogVO —— GET /api/admin/audit 的一行（actor / target 都是 id + 名字快照）
{ "id": 6, "actorId": 1, "actorName": "admin", "action": "DELETE_USER", "actionLabel": "删除用户",
  "targetId": 2, "targetName": "carol", "detail": "级联删除其名下的会话 / 消息 / 附件", "createdAt": "..." }

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

// UpdateProfileRequest（24 号）—— 全空白 = 清空该字段，后端统一存 NULL
{ "nickname": "小明", "email": "me@example.com", "phone": "" }

// UserProfileStatsVO（25 号）
{ "conversationCount": 3, "messageCount": 10, "attachmentCount": 2 }

// BatchUserRequest（27 号）—— 一次只做一个动作；SET_ROLE 带 role，RESET_PASSWORD 二选一
{ "ids": [8, 9], "action": "SET_ROLE", "role": 3 }
{ "ids": [8, 9], "action": "RESET_PASSWORD", "generate": true }

// BatchUserResultVO（27 号响应）—— 逐条结果：失败带中文原因，随机密码只回显这一次
{
  "requested": 2, "succeeded": 1, "failed": 1,
  "items": [
    { "id": 8, "username": "test_user", "success": true,  "message": null, "generatedPassword": null, "user": { "…": "最新一行" } },
    { "id": 1, "username": "admin",     "success": false, "message": "不能修改自己的账号状态", "generatedPassword": null, "user": null }
  ]
}

**状态码语义**

| 码 | 触发场景 |
|---|---|
| 400 | `@Valid` 校验失败（新密码长度不符）、请求体不是合法 JSON、原密码不正确、新密码与原密码相同、**文本和图片同时为空**、附件 id 不可用（不存在 / 属于别的会话 / 已被别的消息占用）、带图但模型不在 `llm.vision-models` 里、上传 MIME 不在白名单、单张超 5MB；**管理端**：分页越界、未知角色/状态、用户名已存在、数据冲突、自我保护规则（见 8.6） |
| 413 | 上传超过 `spring.servlet.multipart.max-file-size`（multipart 解析阶段就抛，`GlobalExceptionHandler` 接住给中文文案） |
| 401 | 未带 token、token 格式错/签名错/已过期、账号已被删、改过密码导致旧 token 失效、**账号被禁用**（AuthInterceptor 回表发现 `status=DISABLED`，立刻生效）。**前端收到任何 401 都 `clearSession()` 弹回登录页** |
| 403 | 普通用户访问 `@RequireAdmin` 接口（`/api/admin/**`）；**被禁用的账号登录**（文案与 401 同一句，且放在密码校验之后，避免泄露「用户名存在」） |
| 404 | 会话不存在、**会话属于别人**（归属校验刻意不返回 403，免得泄露「这个 id 存在」）、用户不存在、**附件不存在或属于别人的会话**（同一口径） |
| 204 | 删除会话成功；管理端的强制下线（`/revoke`）与删号（`DELETE /api/admin/users/{id}`） |

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
       matches 成功但 user == null → 理论不可达（密码匹配上了随机 UUID 的哈希），兜同一句 401
       status = DISABLED → 403「账号已被禁用，请联系管理员」（**先验密码再报禁用**，否则可枚举）
  → userRepository.touchLastLogin(id, now)   // @Modifying 单列 update，所以 login() 带 @Transactional
  → TokenService.issue(user): payload = {uid, username, role, iat(ms), exp(ms)}
       token = base64url(payloadJson) + "." + base64url(HMAC-SHA256)
  → LoginResponse{token, "Bearer", ttlSeconds, UserVO}

后续请求：Authorization: Bearer <token>
  → AuthInterceptor.preHandle()
       ① handler 不是 HandlerMethod → 放行（CORS 预检 / 静态资源 / 404）
       ② resolveToken()：只认 Bearer 头，不做 ?token= 兜底
       ③ TokenService.verify()：切分 → base64url 解码 → MessageDigest.isEqual 常量时间比签名 → 反序列化 → 比 exp
       ④ userRepository.findById(payload.uid())：账号没了 → 401
       ⑤ UserStatus.isEnabled(user.status) 为假 → 401「账号已被禁用，请联系管理员」
       ⑥ payload.iat() < user.passwordChangedAt(转 epoch ms) → 401「密码已修改，请重新登录」
       ⑦ new CurrentUser(id, username, role)；@RequireAdmin 且非管理员 → 403
       ⑧ request.setAttribute("chatbot.currentUser", currentUser)
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

### 8.6 用户管理流程（/api/admin/**）

权限：`AdminUserController` **类级** `@RequireAdmin`（AuthInterceptor 统一拦，普通用户 403）；控制器薄，业务规则与自我保护全在 `AdminUserService`。

**自我保护规则**（全部 400 + 中文文案。目的：管理员在界面上点错一行，最坏不能把自己锁在系统外）：

| 操作 | 挡住的 case |
|---|---|
| 任何写操作 | 目标 id == 当前用户（`CurrentUser` 形参注入，id 只来自 token）。改角色另有专门文案「不能修改自己的角色」；**界面上自己的角色控件是锁死的**（select 换成纯文本、按钮 disabled），后端这道 400 是防绕过界面直调 |
| 任何写操作 | 目标层级 **≥** 操作者层级（`Roles.rank` 比较）：管理员因此碰不到管理员 / 超级管理员，超级管理员碰不到超级管理员（含自己）。文案：「只能管理层级低于自己的用户：对方是「xxx」」 |
| 建号 / 改角色 | 新角色层级 ≥ 操作者层级：不能造一个和自己平级或更高的账号（所以超级管理员也建不出第二个超级管理员） |
| 降级 / 禁用 / 删除 | 目标是「最后一个**启用的**管理者」：两层闸，① 启用的管理员+超级管理员合计 ≤1 → 400「系统至少需要保留一个启用的管理员」；② 目标是最后一个启用的**超级管理员** → 400「…超级管理员」。已禁用的管理者本来就进不来，不占名额 |

**删号级联**（单事务，顺序不能换——三个外键都是 RESTRICT，先删父行撞 errno 1451）：
`attachment.deleteByOwnerId` → `message.deleteByOwnerId` → `conversation.deleteByOwnerId` → `userRepository.delete`。
与 `ConversationService.delete` 同一套顺序，只是按 owner 一次删完、不逐个会话循环。刻意不做软删除，理由见九 21。

**重置密码 vs 强制下线**：两者都靠推 `password_changed_at` 让旧 token 立刻失效（复用 8.3 的机制，不建会话表）。
区别：重置改密码 + 置 `mustChangePassword=1`（本人下次登录被强制改密）；下线只踢会话，原密码还能登回来——
怀疑 token 泄漏用下线，怀疑密码泄漏才用重置。

**操作审计**：六个写操作（建号 / 改角色 / 启停 / 重置密码 / 强制下线 / 删号）成功后各记一行 `admin_audit_log`，
写入点在 `AdminUserService` 各自的 `@Transactional` 里（经 `AdminAuditService.record()`），与业务同事务。
读取走 `GET /api/admin/audit`（23 号接口），界面是 `/admin/audit`（只读、无清空按钮）。

**批量操作（27 号接口）**：`AdminUserBatchService.execute()` 把六个单条动作在一批 id 上跑一遍。

- **逐条独立事务，不是一个大事务**：勾选里混进一个不能动的人（自己 / 层级不低于自己 / 最后一个启用的管理员）是常态，
  整批回滚会让人「改一个人都得先把不能动的行挑出去」；逐条提交则成功的立刻落地、失败的把原因带回来。
  代价是批量不具备原子性——刻意的取舍，界面上把逐条结果摊开给人看（`BatchResultDialog`）。
- **规则零重写**：层级、自我保护、审计全部由被调用的 `AdminUserService` 单条方法负责；批量服务只做三件事：
  去重与上限（100）、整批级参数校验（未知动作 / 未知角色 / 密码太短 → 一次性 400）、逐条 try/catch 收集结果。
  只 catch `ResponseStatusException` 与 `DataIntegrityViolationException`，别的异常照旧 500。
- **审计不补汇总行**：每条成功的操作各有一行（动作 / 操作者 / 目标都齐）；汇总行没有单一目标，
  塞进「谁对谁做了什么」形状的审计表里，只会让同一次操作在列表里出现两次。
- **必须单独成 Bean**：Spring 事务靠代理，在 `AdminUserService` 内部 this 调自己的 `@Transactional` 方法不生效，
  整批会挤进调用方的一个事务里（或者压根没有事务）。

**种子账号与测试账号（`SeedUserInitializer`）**：三件事都幂等（建超管 / 超管自愈 / 补测试账号），见 2.2 config 表。
超管自愈的触发边界是「系统里一个**启用的**超管都没有」：还有别的启用超管时，种子账号被降级是管理者的决定，
启动流程不改回去；一个都没有时不提回来，系统就再也创建 / 提升不出管理员（层级规则下的死局）。

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
14. **日志不打密码**：`SeedUserInitializer` 建初始管理员、补测试账号时故意不打印任何密码（含测试账号的统一密码），因为日志会被收集和转发。

15. **会话归属校验**：会话接口的控制器都接 `CurrentUser`，service 层按 `(id, owner_id)` 查；不存在**或属于别人**一律 404 而不是 403——403 会把「这个 id 确实存在」泄露出去，而 id 是自增的，等于让人枚举出全站有多少会话。管理员**没有**跨用户查看 / 删除的特权，真要做跨用户管理得单独设计（审计、转交、级联删），别指望在现有接口上加个角色判断就完事。

16. **CORS 白名单来自配置**：`cors.allowed-origins` 默认只有 Vite 的 5173，对外部署用 `CORS_ALLOWED_ORIGINS` 覆盖；留空启动失败而不是退化成 `*`。非白名单源的跨域请求拿 403 且不带 `Access-Control-Allow-Origin` 头，同源请求和服务器间调用（不带 Origin 头）不受影响。
17. **人设隐私**：`system_prompt` 只在用户自己的 `/me`、登录响应和改人设响应里出现；管理员视角的 `AdminUserVO` 连这个字段都没有——「能列用户」不等于「能看别人的设置」。改人设**不换发 token**（与改密码刻意不同）：它不是安全事件，不该踢掉自己的其他设备。 |
19. **管理端自我保护 = 层级模型**：`Roles.rank()` 超级管理员 3 > 管理员 2 > 普通用户 1 > 访客 0，所有写操作要求「目标层级严格低于操作者」，另加「不能对自己下手」「不能指派不低于自己的角色」「最后一个启用的管理者 / 超级管理员动不得」三道闸，一律 400 中文文案。**自己的角色在界面上是锁死的**（控件 disabled + 纯文本标签），不是「看起来能点、点了才报错」。层级与 code 解耦：code 是落库值（0/1 有历史含义不能改），rank 才是权限顺序。
20. **随机密码只回显一次**：`ResetPasswordResult.generatedPassword` 只在那一次响应里出现，后端不留明文（库里只有 BCrypt 哈希）；前端弹窗关掉就再也看不到，界面上明确写「只显示这一次」。
21. **删号不做软删除是刻意的**：`owner_id` 是 NOT NULL + RESTRICT，软删除就得给所有会话找一个「已注销」替身账号，反而造出一个谁都能看见的幽灵用户。「谁动过这个账号」的留痕由 `admin_audit_log` 承担（见 8.6），不靠留着登录行。
22. **审计与业务同事务、且只记成功**：`AdminAuditService.record()` 必须在调用方自己的 `@Transactional` 里调——操作回滚则审计一起回滚，否则会出现「操作失败了却有一条留痕」的假记录，比没有审计更误导。审计表不建外键、应用层不开删除入口（repository 里没有 update/delete 方法），清历史只能走运维 SQL。
23. **种子账号必须是超级管理员**：层级规则下管理员管不到管理员，若种子只是管理员，系统将永远无法创建 / 提升出超级管理员（没人有指派权限）。`SeedUserInitializer` 与 `init.sql` 的种子都是 role=2；老库（种子 role=1）有两条升级路径：手工执行 `init.sql`「已有库升级：角色层级」段里那条 UPDATE，或者直接启动新版后端让**超管自愈**把它提回来（触发条件：系统里一个启用的超管都没有）。
24. **批量不绕过任何单条规则**：`POST /api/admin/users/batch` 只是把单条方法在一批 id 上跑一遍，层级 / 不能对自己下手 / 最后一个启用的管理者三道闸逐条生效；「批量」不是特权通道，想靠批量改掉单条接口改不动的人是不可能的。
25. **个人信息页改不了登录名与角色**：`UpdateProfileRequest` 只有 nickname / email / phone 三个字段。username 是审计快照里「谁干的」的锚点，允许本人随手改名等于允许抹线索；role / status 是管理端写口径，放进「改自己的资料」就是现成的提权洞。
18. **禁用立刻生效，且不留枚举口子**：AuthInterceptor 每请求回表，所以禁用后目标账号的**下一个请求**就 401，不用等 token 自然过期；登录接口对禁用账号返回 403，但**放在密码校验之后**——顺序反了的话，「这个用户名存在但被禁用了」就成了一个可枚举的信息。已知限制：正在跑的 SSE 不会被打断（拦截器只在请求开始时执行）。 |
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

1. MySQL 建库：执行 `chatbot/sql/init.sql`（或直接启动后端靠 `ddl-auto=update` + `SeedUserInitializer`，它会连测试账号一起补齐）。
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
| 改端口 | `application.properties` 的 `server.port` + `chatbot-web/vite.config.ts` 代理 target（可用 `BACKEND_URL` 覆盖）+ 根 `README.md` + 本文 5.1/5.2 |
| 改 `UserVO` / `AdminUserVO` 字段 | 本文 2.2 dto 表 + `chatbot-web/src/types.ts` 的 `CurrentUser` / `AdminUser`（**新字段一律可选**：localStorage 里的旧登录态没有它）+ 用到它的界面（显示名统一走 `auth.ts` 的 `displayName`） |
| 新增平级功能模块 | `views/` 下组件 + `router/routes.ts` 一条带 `meta.moduleId` 的记录 + 本文 2.1/4.1 + `chatbot-web/README.md` 目录结构段；管理端接口挂 `/api/admin/**` |
| 加批量类接口 | 逐条事务的编排**单独成 Bean**（同类内 this 调用不走代理）+ 响应必须是逐条结果而不是总数 + 本文 7.1/7.2/8.6 |
| 改鉴权机制（token 格式、密钥、失效规则） | 本文 **3.2 / 8.2 / 九** + `chatbot/README.md`「接口」段 |
| 新增前端依赖 | `chatbot-web/package.json` + 本文 **一、技术栈** + 4.1（若引入新的渲染方式，例如真的加了 Markdown 渲染，要同时改 `chatbot-web/README.md` 和本文 13.3） |
| 改默认标题文案 | `service/ChatService.java` 的 `DEFAULT_TITLE` + `chatbot-web/src/views/ChatView.vue` 的 `DEFAULT_TITLE` + `ConversationService.create()` + 本文 6.1（**四处必须一致**，前端靠标题字符串识别空会话） |
| 改标题截断长度 | `ChatService.TITLE_MAX_LENGTH` + 本文 2.2 / 6.1 / 8.1 |
| 改 SSE 超时余量 | `ChatService.SSE_TIMEOUT_MARGIN_MS` + 本文 2.2 / 8.1 + `chatbot/README.md`「停止生成」段 |
| 新增全局 CSS 类 | `chatbot-web/src/assets/main.css` + 本文 4.1（main.css 行） |
| 新增页面 / 平级模块 | `chatbot-web/src/views/` + `router/routes.ts` 一条记录（+ `src/api/<模块>.ts`）+ 本文 2.1 / 4.1 / 4.2 组件树 + `chatbot-web/README.md`「目录结构」段 |
| 新增管理端接口 | 挂 `/api/admin/**` + 类级 `@RequireAdmin` + `Admin*` 命名 + 本文 7.1 / 7.2 / 8.6 / 九 + `chatbot/README.md`「接口」段 |
| 完成「后续待加」里的某项 | 删掉 `chatbot/README.md` 末尾对应条目 + 更新本文十二、十三节 |
| 改会话归属 / 消息分页这类「表结构 + 接口形状」联动 | 6.1 表结构 + `sql/init.sql`（含「已有库升级」段）+ 7.1 / 7.2 + `api.ts` / `types.ts` + `ChatView.vue` + `chatbot-web/README.md` |
| 上述任何一项 | **刷新本文文末「最后更新」日期，并在第十四节变更记录追加一行** |

同步时请保持本文风格：表格化、写「为什么」、标注刻意的设计取舍，不要写成流水账。

---

## 十二、扩展点

1. **换 LLM 服务商**：改 `llm.base-url` + `llm.model` + `llm.api-key` 即可，任何 OpenAI 兼容接口都能直连。服务商不认识 `enable_thinking` 时，把 `application.properties` 里那一行整行注释掉（`LlmProperties.enableThinking` 变 null → 请求体不下发该字段）。要支持非兼容协议，新写一个 `LlmClient` 实现并在 `LlmConfig` 里加分支。
2. **新增角色 / 细粒度权限**：在 `Roles` 加常量，新增注解（如 `@RequireOperator`），在 `AuthInterceptor.requiresAdmin()` 旁边加对应判断。`sys_user.role` 是 int，**不用改列类型**。
3. **新增接口**：`controller/` 下加方法即可，默认要登录；需要当前用户就在形参里声明 `CurrentUser`。**管理端接口挂 `/api/admin/**` 并把 `@RequireAdmin` 打在类上**（该控制器下不存在普通用户该能调的方法时，类级注解不给漏打留机会）；`/api/users/**` 只放「管自己」的接口。
6. **新增管理台模块**：后端 `Admin<模块>Controller/Service` + 前端 `views/<模块>/` + `src/api/<模块>.ts` + `routes.ts` 一条带 `requiresAdmin` 的记录；下拉字典照 `/options` 模式做（中文 label 后端给，前端不写死枚举值）。
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
8. **批量操作不具备原子性（刻意）**：逐条独立事务，「成功 3 / 失败 1」是正常结果而不是需要重试的错误（理由见 8.6）。勾选只限当前页、翻页即清空，也是同一取向：不在眼前的人不该被一个「删除 17 个」的按钮带走。操作审计仍只覆盖用户管理的写操作，未来其他管理模块接入时要各自调 `AdminAuditService.record()`。

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
34. **管理台分页是 offset（`PageVO`），消息历史是游标（`MessagePageVO`），两套别混**：管理台要总数和「第几页」，聊天只要「还能不能往前翻」；给消息列表加 total 等于在 LONGTEXT 大表上多跑一次 count(*)。
35. **LIKE 关键字必须转义 `% _ \`**（`UserSpecifications.escapeLike`）：否则搜「100%」变成前缀匹配、搜「_」命中所有人；用户输入不该被当 SQL 通配符。`toLowerCase` 用 `Locale.ROOT`，跟着系统区域走会在土耳其语环境搜不出结果。
39. **左下角账号菜单不能居中弹出**：导航条只有 56px 宽，菜单比它宽得多，`left:50% + translateX(-50%)` 会让菜单左半截跑出视口被裁掉（2026-09-25 之前的 bug 正是这个，表现为「点左下角头像，菜单显示不全」）。正确姿势是贴导航条**右侧**弹（`left: calc(100% + 10px)`、底边与头像对齐），菜单再宽也只往内容区里长。
40. **批量接口不要做成一个大事务**：见 8.6。大事务的后果是「勾错一个人、整批白干」，且失败原因只剩一句笼统的回滚信息；逐条事务 + 逐条结果才是管理台该有的样子。
41. **第二个前端 dev server 必须进 CORS 白名单**：浏览器对**同源 POST 也会带 `Origin` 头**，后端白名单不含该源时会返回**空响应体的 403**（前端只能显示「请求失败（HTTP 403）」，极易误判成密码错）。用 `BACKEND_URL` 起第二个前端、后端跑在别的端口时，`CORS_ALLOWED_ORIGINS` 要把新源一起加上。
38. **`sys_user.role` 的 code 与层级（rank）是两回事，别拿 code 比大小**：code 里 `GUEST=3` 比 `ADMIN=1` 大，但层级最低；所有「谁能管谁」的判断必须走 `Roles.rank()`。前端同理：不要写 `role > 1` 这类判断。
39. **`/options` 的 `roles` 是「可指派」不是「可筛选」**：它按操作者层级过滤过，管理员那份里根本没有「管理员 / 超级管理员」。列表的角色筛选器必须用 `allRoles`，否则管理员在表格里看得见管理员行、筛选器里却筛不出来（看得见、筛不出）。以后任何「按角色过滤」的读路径都走 `allRoles`，写路径才走 `roles`。
37. **审计的 `action` 是字符串不是 ENUM，别「顺手」改成 enum**：加审计动作应该是零迁移的（往 `AuditAction` 加常量即可）；改成 ENUM 每加一个动作都要手工 ALTER 列。`AuditAction.label()` 对不认识的动作名**原样返回**，是因为回滚过版本后表里可能出现新代码不认识的老动作，界面不该因此整页崩。
36. **重置密码的 `generatedPassword` 不要存进任何前端状态**：它只在响应里出现一次；弹窗展示后随组件卸载丢弃。存 localStorage 等于把明文密码写盘。
33. **本机 `mvnw` 默认跑在 JDK 1.8 上**（`JAVA_HOME` 指向 `C:\Program Files\Java\jdk-1.8`）：record、`instanceof` 模式匹配全不认识，报一堆「需要 class, interface, enum」，看起来像代码写错了其实是工具链。编译 / 启动本项目前必须 `JAVA_HOME` 指到 26（IntelliJ 装在 `~/.jdks/openjdk-26.0.2.1`），IDE 里跑不受影响是因为 IDE 用自己下载的 JDK。
24. **历史 token 预算是估算值**：没有分词器可用，按「中文 1 字 1 token、其余 4 字符 1 token」近似；预算是保护性上限不是精确配额。每轮真实上下文大小以用量里的 `prompt_tokens` 为准（界面已显示），两者对不上时信后者。 |

### 13.3 文档偏差记录（2026-09-21 已全部修正，2026-09-24 追加第 5 条，2026-09-25 追加第 6、7 条）

首次全量通读时发现三份 README 有 4 处与代码不符，现已按实际情况改写。**这张表留着是为了说明「为什么这几处的措辞是现在这样」**，不要把它们又改回原来的说法。

| # | 位置 | 原说法（错） | 已改为 |
|---|---|---|---|
| 5 | 本文 2.2 controller 表下的注意 | 「`ConversationController` 和 `ChatController` 都没有 `CurrentUser` 形参，会话操作不做归属校验」 | 会话隔离（2026-09-24 第二次变更）落地后每个方法都带 `CurrentUser`、service 层做归属校验；本次改为「每个方法都声明 `CurrentUser`」，并保留一句说明那是隔离前的旧状态 |
| 1 | `chatbot-web/README.md`「SSE 流式解析」末句 | `MessageBubble.vue` 负责 **Markdown 渲染**、思考折叠和打字效果 | 明确写「正文是纯文本（`{{ message.content }}` + `white-space: pre-wrap`），项目没有引入任何 Markdown 渲染库」，并指出要加 Markdown 得先引依赖再同步本文 4.1 与 13.1 |（**2026-09-24 起正文已改为 Markdown 渲染**，见 4.1 的 `MarkdownContent.vue`；这两行保留是为了说明当初为什么那样改）
| 2 | `chatbot-web/README.md`「目录结构」 | `MessageBubble.vue  消息气泡，区分 user / assistant` | 补上「纯文本渲染（无 Markdown）+ 思考折叠 + 打字光标」 |（同上：`MessageBubble` 现在把助手消息交给 `MarkdownContent`，用户消息仍纯文本）
| 3 | `chatbot/README.md`「错误响应体」正文 **和** 配置表 | 由 `GlobalExceptionHandler` + **`server.error.include-message=always`** 保证（配置表里还列了这一项） | 改为「由 `GlobalExceptionHandler` 自己组装 `ErrorResponse`，**不依赖** `server.error.include-message`」，并**删掉了配置表里那一行**——`application.properties` 里从来没有这项配置 |
| 4 | 根 `README.md`、`chatbot/README.md`、`chatbot-web/README.md` | 「multi-user auth」/「多用户」容易被读成「每个用户有自己的会话」 | 三份 README 各加一处显式说明：多用户只指鉴权与用户管理，会话全站共享、可互相读写删除，并指向本文 13.1 |（**该说明已于 2026-09-24 被「会话按用户隔离」取代**：三份 README 又改写了一次，侧边栏现在就是「我的会话」）
| 6 | `chatbot-web/README.md` 开头第一句 | 「**没有 vue-router**——页面切换就是换根组件」 | 2026-09-25（第十五次变更）已引入 vue-router：`router/{index,routes,guards,paths}.ts` + `layouts/AppShell.vue`，权限闸门在守卫里。该句是引入前的残留，本次改为路由约定说明 |
| 7 | `chatbot/sql/init.sql` 末尾备注 2) | 「删除用户目前不是功能，所以 `fk_conversation_owner` 用的是默认的 RESTRICT」 | 删号早已是功能（22 号接口，级联删）。RESTRICT 保留是**刻意的**：级联顺序写在应用层（`AdminUserService.delete`），删用户行之前先抓用户名快照进审计表；改成 `ON DELETE CASCADE` 会把这条线索一起级联掉。本次改写备注并说明别顺手改 CASCADE |

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
| 2026-09-25（第十六次） | **账号状态基座（用户管理一阶段）**。`sys_user` 加三列：`status int NOT NULL DEFAULT 0`（0 启用 / 1 禁用）、`last_login_at datetime(6) NULL`、`must_change_password tinyint(1) NOT NULL DEFAULT 0`；新增 `auth/UserStatus.java`。`AuthInterceptor` 回表后加禁用检查 → 401「账号已被禁用」（立刻生效）；`UserService.login()` 加 `@Transactional` + 密码校验后判禁用 → 403 + `touchLastLogin()`；本人改密成功后清 `mustChangePassword`；`UserVO` 加 `mustChangePassword`（登录与 `/me` 都带，前端强制改密框在 T6 接）。**不需要删库**：`ddl-auto=update` 直接补列，带 DEFAULT 的 NOT NULL 列让老行填 0，不会锁任何人；`init.sql` 的 CREATE 与「已有库升级」段同步。临时库实测：禁用后旧 token 立刻 401、登录 403、恢复+置标记后登录带 `mustChangePassword=true`、本人改密后清除。6.1 / 7.2 / 8.2 / 九 同步；另发现并记录：本机 `mvnw` 默认走 JDK 1.8（`JAVA_HOME` 指向 jdk-1.8），编本项目必须用 JDK 26 |
| 2026-09-25（第十七次） | **管理端接口（用户管理二阶段）**。`/api/admin/users` 八个接口（列表分页+筛选 / options 字典 / 建号 / 改角色 / 启停 / 重置密码 / 强制下线 / 删号级联），类级 `@RequireAdmin`；新增 `AdminUserService` / `AdminUserController` / `UserSpecifications` / `PageVO` 等 8 个 DTO；三个 repository 加 `deleteByOwnerId`；`GlobalExceptionHandler` 加 `DataIntegrityViolationException` → 400。自我保护规则与删号级联见新增的 8.6；九 加 19~21 条、13.1 加第 7 条、13.2 加 34~36 条。同时删除遗留的 `GET /api/users`（唯一调用方是只读弹窗）。临时库实测八个接口的成功 / 400 / 403 / 404 与删号级联 |
| 2026-09-25（第十八次） | **用户管理页面（/admin/users）**：第一个和聊天平级的功能模块。`views/admin/UsersView.vue`（表格 + 行内改角色/启停 + 搜索筛选 + offset 分页）、`components/admin/{UserFormDialog,ResetPasswordDialog}.vue`、`components/common/ConfirmDialog.vue`（删号要求输入用户名）、`api/userAdmin.ts`、`types.ts` 管理端类型、`routes.ts` 注册（懒加载、独立分包）；`AppShell` 挂关不掉的强制改密框（`ChangePasswordDialog` 加 `force`），`ModuleNav` 底部加账号按钮 + 退出登录（管理页没有聊天顶栏的头像菜单）。浏览器实测：建号/禁用/重置/删号全链路、普通用户送 /403 且导航只剩聊天、强制改密框自动弹出并可完成 |
| 2026-09-25（第十九次） | **用户管理入口从弹窗改为平级模块跳转**：`UserMenu` 的「用户管理」`router.push('/admin/users')`；删除 `UserListDialog.vue` 与 `api.ts` 的 `listUsers`（留着就是「两个地方都能看用户列表」的重复入口）。ChatView 只动 5 行 |
| 2026-09-25（第二十次） | **操作审计日志**。新表 `admin_audit_log`（actor/target 存 id+名字快照、不建外键；action 存字符串不用 ENUM）+ `entity/AuditAction` + `AdminAuditLogRepository`（只读不改）+ `AdminAuditService`（record 与业务同事务 / page）+ `AdminAuditController`（`GET /api/admin/audit`，23 号接口）+ `AdminAuditLogVO`；`AdminUserService` 六个写操作各记一行（`create` 补 `CurrentUser` 形参）。前端 `views/admin/AuditView.vue`（`/admin/audit`，hidden 路由，UsersView 头部「操作记录」链接进入，只读无清空按钮）+ `api/userAdmin.ts` 的 `listAdminAudit`。临时库实测：六个动作各留一行、失败操作（400）不留痕、删号后靠 target_name 认人、非管理员 403。6.1 加表结构、7.1 加 23 号、8.6 加审计段、九 加 22 条、13.1 第 7 条改写、13.2 加 37 条 |
| 2026-09-25（第二十一次） | **角色层级模型**：新增 `SUPER_ADMIN=2`（超级管理员）与 `GUEST=3`（访客：应用内权限同普通用户、层级最低，作「降权但不禁用」的承接位）；`Roles.rank()` 表达层级，管理端所有写操作要求「目标层级严格低于操作者」，建号/改角色要求「新角色层级严格低于操作者」，自己的角色界面锁死 + 后端 400 双保险；「最后一个启用的管理者 / 超级管理员」两道闸。种子账号（`AdminUserInitializer` 与 `init.sql`）改为超级管理员，`init.sql` 附老库升级 UPDATE。`AdminUserVO` 加 `canManage`、`/options` 的 roles 按操作者层级过滤，前端锁死行把 select 换成纯文本标签。临时库实测：超管/管理员/访客三种视角的 canManage 与控件锁死状态、越级操作 400 文案、指派平级角色 400、访客 403。6.1 / 7.2 / 8.6 / 九 / 13.2 同步 |
| 2026-09-25（第二十二次） | **`/options` 拆成「可指派」与「可筛选」两套角色字典**：`UserAdminOptionsVO` 加 `allRoles`（全量角色，层级从高到低），`roles` 语义不变（层级严格低于操作者）；前端 `types.ts` 同步，`UsersView.vue` 的角色**筛选器**改用 `allRoles`（行内改角色与新建弹窗仍用 `roles`）。补的是第二十一次留下的读路径缺口：管理员在列表里看得见管理员 / 超管的行（控件锁死），筛选器却只有「普通用户 / 访客」两档。临时库 + 浏览器实测：超管 roles=3 / allRoles=4，管理员 roles=2 / allRoles=4；管理员按「管理员」「超级管理员」筛选各命中 1 行且 canManage=false、控件全锁；新建弹窗的角色下拉仍无「超级管理员」。2.2 / 4.1 / 7.1 / 7.2 / 13.2 第 39 条同步 |
| 2026-09-25（第二十三次） | **个人信息 + 管理台批量 + 种子账号自愈**。① 个人信息：`sys_user` 加 `nickname` / `email` / `phone` 三列（均可空）；新增 24~26 号接口（`PUT /users/me/profile`、`GET /users/me/stats`、`POST /users/me/revoke`）与 `UpdateProfileRequest` / `UserProfileStatsVO`；`UserVO` 带上新字段与 status/lastLoginAt；前端新增平级模块 `/profile`（`ProfileView.vue` + `IconUser.vue`，改密码 / 改人设 / 强制改密复用既有弹窗），`auth.ts` 加 `displayName`（昵称优先），顶栏与左下角菜单的头像首字母跟着昵称走。② 管理台批量：27 号接口 `POST /api/admin/users/batch`（六个动作、`ids` 上限 100、逐条独立事务、响应逐条结果）+ `AdminUserBatchService`（单独成 Bean，理由见 8.6）+ `BatchUser*` 三个 DTO；前端 UsersView 加多选与批量条、`BatchResetPasswordDialog` / `BatchResultDialog`。③ 种子：`AdminUserInitializer` 改名 `SeedUserInitializer`，新增「超管自愈」（系统无启用超管时把种子账号提回超管，老库 role=1 自动升级）与「每角色一个测试账号」（test_super / test_admin / test_user / test_guest，密码统一 test123456，`auth.seed-test-users` 可关）；`init.sql` 同步种子 role=2、测试账号 INSERT 与两段升级 SQL。④ 修左下角账号菜单居中弹出导致左半截出屏（13.2 第 39 条）。浏览器 + 接口实测：菜单完整可见、资料保存后导航头像跟随、批量禁用/启用/改角色/重置/下线/删除的逐条成功与逐条失败文案、层级越界 400、普通用户 403、revoke 后旧 token 401 且原密码可重登。7.1 / 7.2 / 8.6 / 九 / 13.1 / 13.2 / 13.3 同步 |
*最后更新：2026-09-25*

