# chatbot 后端

类 ChatGPT 网页版的 Spring Boot 后端：会话管理 + 消息存储 + SSE 流式对话 + 自签 token 鉴权。
LLM 走百炼（OpenAI 兼容接口），未配 key 时自动用本地 Mock。

> 启动步骤见仓库根目录 [README.md](../README.md)，架构全貌见 [PROJECT_OVERVIEW.md](../PROJECT_OVERVIEW.md)。
> 本文只写**后端对外的契约**和**配置取舍**——那些读代码读不出来的东西。

## 接口

全量接口表见 PROJECT_OVERVIEW.md 第七节，此处不重复。三条前提：

- `/api/**` 除 `POST /api/auth/login` 外全部要求 `Authorization: Bearer <token>`（见 `WebConfig.PUBLIC_PATHS`）。
  白名单模式：新加的接口默认受保护，不用改配置。
- token 是自签 HMAC-SHA256，无状态。`auth.token-secret` **少于 32 字符后端直接启动失败**；
  改密码后旧 token 立即失效（payload 的 `iat` 用毫秒，与 `password_changed_at` 比对）。
- **人设只属于自己**：`PUT /api/users/me/system-prompt` 改的是 token 里那个用户；管理员视角的 `GET /api/admin/users` 返回 `AdminUserVO`，**连 `systemPrompt` 字段都没有**。
  人设每轮作为 system 消息放在历史最前面，**计入输入 token**——写两千人设每轮就烧两千字输入钱。
- **会话按用户隔离**：`conversation.owner_id` 记录归属，会话相关接口的控制器都接 `CurrentUser`，校验统一在 service 层。
  查不到或不是自己的会话**一律 404，不返回 403**——403 会把「这个 id 确实存在」泄露出去，而 id 是自增的，
  等于让人枚举出全站有多少会话。管理员也没有跨用户特权。
- **消息接口是分页的**：`GET /api/conversations/{id}/messages` 返回 `{items, beforeId, hasMore}` 而不是数组，
  `before`（id 游标）和 `limit`（默认 50、上限 200）走 query 参数。用游标不用 offset：
  一边翻页一边有新消息入库时，offset 分页会重复或漏消息。
- **图片走两步**：先 `POST /api/conversations/{id}/attachments`（multipart 字段名 `file`）拿附件 id，
  再在 `ChatRequest.attachmentIds` 里带上。字节存 MySQL `attachment.data`（LONGBLOB），
  读取走 `GET /api/attachments/{id}` —— **要登录、按会话归属校验（404 口径与会话一致）**，
  所以前端只能 fetch 成 blob 再转 objectURL，不能用 `<img src>`，也不做 `?token=` 兜底。
  单张 ≤5MB、每条消息 ≤4 张、MIME 白名单 5 种位图（**无 SVG**）。
- **管理端全在 `/api/admin/**`，类级 `@RequireAdmin`**：列表（分页+筛选）/ 字典 / 建号 / 改角色 / 启停 /
  重置密码 / 强制下线 / 删号。自我保护规则（不能对自己下手、不能动掉最后一个启用的管理员）一律 400 中文文案；
  删号级联删 附件→消息→会话→用户，单事务、**不做软删除**（留用户行就得造一个幽灵替身账号）。细节见 PROJECT_OVERVIEW.md 8.6。
- **管理端操作有审计**：六个写操作（建号 / 改角色 / 启停 / 重置密码 / 强制下线 / 删号）成功后各写一行 `admin_audit_log`，
  与业务**同事务**（操作回滚则不留痕）；读取走 `GET /api/admin/audit`（同样类级 `@RequireAdmin`）。
  审计表不建外键（管理员自己也可能被删）、应用层不开删除入口——能删的审计不叫审计。
- **角色是层级模型**（`Roles.rank`）：超级管理员(2) > 管理员(1) > 普通用户(0) > 访客(3)，管理操作只允许「上对下」：
  管理员碰不到管理员 / 超管，谁都不能改自己的角色（界面锁死 + 后端 400），也不能指派不低于自己的角色。
  种子账号是**超级管理员**；老库（种子 role=1）要么手工执行 `sql/init.sql` 升级段那条 UPDATE，
  要么直接启动新版后端——`SeedUserInitializer` 在「系统里一个启用的超管都没有」时会自动把种子账号提回来。
- **批量操作 = 单条动作在一批 id 上跑一遍，不引入任何新规则**：`POST /api/admin/users/batch`
  （启用 / 禁用 / 改角色 / 重置密码 / 强制下线 / 删除，`ids` 上限 100）。**逐条独立事务、响应逐条结果**：
  勾选里混进一个不能动的人（自己 / 层级不低于自己 / 最后一个启用的管理员）时，那一条失败并带中文原因，
  其余照做——「成功 3 / 失败 1」是正常结果，不是需要重试的错误。层级 / 自我保护 / 审计全部复用单条实现，
  批量不是特权通道。逐条事务的编排单独成 Bean（`AdminUserBatchService`）：同类内 this 调用不走代理，事务会静默失效。
- **「管自己」的接口有五个**：改密码、改人设、改资料（`PUT /api/users/me/profile`：昵称 / 邮箱 / 手机号）、
  使用统计（`GET /api/users/me/stats`）、退出所有设备（`POST /api/users/me/revoke`，含当前这台，204）。
  **登录名与角色不可自改**：username 是审计快照里「谁干的」的锚点，role / status 是管理端写口径，
  放进「改自己的资料」就是现成的提权洞。改资料不换发 token（不是安全事件），改密码与 revoke 会作废旧登录态。
- **带图请求打到不支持图片的模型会 400**（`llm.vision-models` 白名单），错误文案里列出可用模型。
  校验顺序是刻意的：附件合法性与模型白名单都在用户消息落库**之前**；只有「历史窗口里有旧图、用户刚换了非视觉模型」
  这一种情况会在落库后报 400——那种情况下用户消息还在，换回视觉模型点重新生成即可恢复。

会话标题：发出第一条消息时，自动用该消息前 30 字替换「新的对话」，前端侧边栏可以直接显示。

## SSE 事件契约（POST /api/conversations/{id}/chat）

JSON，null 字段不输出：

| 事件 | 形状 | 说明 |
|---|---|---|
| reasoning | `{"type":"reasoning","content":"..."}` | 思考过程增量，出现在正式回答之前。仅推理模型且 `llm.enable-thinking=true` 时出现 |
| delta | `{"type":"delta","content":"..."}` | 正式回答的增量文本，逐段推送 |
| done | `{"type":"done","messageId":123,"model":"...","prompt_tokens":4,"completion_tokens":30,"reasoning_tokens":12}` | 生成结束，助手消息已入库；**附带 model 与用量**（NON_NULL），前端当场回填用量行 |
| error | `{"type":"error","content":"..."}` | 出错。**文案在 `content`，不是 `message` 字段** |

`reasoning` 是过程展示，不属于助手消息正文：正文 `content` 永远只含 delta 拼出来的回答。思考全文在生成结束时随助手消息入库（`message.reasoning`），刷新页面后折叠块还能展开重读；没开启思考时该列为 NULL、接口也不下发这个字段。
前端不认识某个 type 时忽略即可，delta / done / error 的老契约没变。
建议用法：收到第一帧 reasoning 就显示「思考中…」并可折叠展示，收到第一帧 delta 再切到正文。

`POST /api/conversations/{id}/regenerate` **复用这一套事件**，没有第二份 SSE 契约：它先删掉最后一条助手消息，再用它前面那条用户消息重跑生成；请求体可省略（只带 `enableThinking` 或干脆不带）。最后一条不是助手消息、或会话是空的，返回 400。

### 为什么必须转发思考

推理模型可能先思考几十秒到几分钟才开始输出回答。这段时间如果一帧都不发，
浏览器 / Vite 代理 / Nginx 常见的 60 秒空闲超时会直接掐断连接——那时回答还没开始，一个字都存不下来。
实测转发思考后帧间隔最大 **356ms**，不再触发空闲超时。

### 停止生成

前端直接 abort 那个 fetch 即可，**不需要额外接口**。后端检测到连接断开就停止调用模型，
已生成的部分照样入库，刷新页面能看到。思考阶段点停止同样立刻生效（思考增量也检查取消标志），
但那时还没有正式回答，所以不会写入空的助手消息。
SSE 超时 = `llm.request-timeout-seconds` + 30 秒，走同一条逻辑。

前端怎么解析这个流（EventSource 用不了、TextDecoder 的坑）见 [chatbot-web/README.md](../chatbot-web/README.md)。

## 错误响应体（非 SSE 接口）

统一结构，前端读 `message` 直接展示、读 `status` 做分支：

    {"timestamp":"...","status":404,"error":"Not Found","message":"会话不存在: 3","path":"/api/conversations/3/messages"}

由 `GlobalExceptionHandler` 自己组装 `ErrorResponse` 保证，**不依赖 `server.error.include-message`**——
`application.properties` 里没有这一项：它只影响 Spring 默认 `/error` 的输出，而本项目所有错误都走 `@RestControllerAdvice`，
message 是处理器自己填进去的。另外每个 handler 都显式设了 `Content-Type: application/json`，
否则内容协商会因为 `Accept: text/event-stream` 变成 406，把真实状态码盖掉。
chat 接口出错时返回的是上面的 SSE `error` 事件，不是这个结构。

账号被**禁用**时的两个状态码，文案是同一句「账号已被禁用，请联系管理员」：
已登录的下一个请求 **401**（`AuthInterceptor` 每请求回表，立刻生效，不用等 token 过期）；登录接口 **403**，
且**放在密码校验之后**——顺序反了的话「用户名存在但被禁用」就成了可枚举信息。
`must_change_password=1` 的账号登录会拿到 `UserVO.mustChangePassword=true`，前端据此强制改密。

## 手动测试

所有接口都要先登录换 token：

```powershell
$tok = (curl.exe -s -X POST http://localhost:8089/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}' | ConvertFrom-Json).token
$h = "Authorization: Bearer $tok"

curl.exe -s -X POST http://localhost:8089/api/conversations -H $h
curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":"你好"}'
curl.exe -s "http://localhost:8089/api/conversations/1/messages?limit=5" -H $h   # 分页：返回 {items, beforeId, hasMore}
curl.exe -s -X PUT http://localhost:8089/api/conversations/1/title -H $h -H "Content-Type: application/json" -d '{"title":"新名字"}'
curl.exe -N -X POST http://localhost:8089/api/conversations/1/regenerate -H $h -H "Content-Type: application/json" -d '{}'
curl.exe -s http://localhost:8089/api/llm/options -H $h
curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":"你好","model":"qwen3.8-max","thinkingBudget":16384}'
curl.exe -s -X PUT http://localhost:8089/api/users/me/system-prompt -H $h -H "Content-Type: application/json" -d '{"systemPrompt":"你是资深 DBA，只回答数据库问题"}'
curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":"今天上海天气","enableSearch":true}'
curl.exe -s -i http://localhost:8089/api/conversations/99999/messages -H $h
curl.exe -s "http://localhost:8089/api/admin/users?page=0&size=20" -H $h          # 管理端：分页列表
curl.exe -s http://localhost:8089/api/admin/users/options -H $h                   # 字典：roles=可指派（按层级过滤）/ allRoles=全量（筛选用）
curl.exe -s -X POST http://localhost:8089/api/admin/users -H $h -H "Content-Type: application/json" -d '{"username":"alice","password":"alice-123456"}'
curl.exe -s -X PUT http://localhost:8089/api/admin/users/2/status -H $h -H "Content-Type: application/json" -d '{"status":1}'
curl.exe -s -X POST http://localhost:8089/api/admin/users/2/password -H $h -H "Content-Type: application/json" -d '{"generate":true}'
curl.exe -s -i -X DELETE http://localhost:8089/api/admin/users/2 -H $h             # 删号：204
curl.exe -s "http://localhost:8089/api/admin/audit?page=0&size=20" -H $h            # 操作审计：id 倒序
curl.exe -s -i -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":""}'
```

不带 token 时这四条全部返回 401：拦截器跑在 `@Valid` 和控制器之前，
所以后两条想看到 404 错误体和 400 校验错误，必须先带上 token。
管理端那六条还要是**管理员**的 token：普通用户 token 一律 403「需要管理员权限」。

## 配置

| 配置 | 默认 | 说明 |
|---|---|---|
| server.port | 8089 | |
| auth.token-secret | dev 占位值 | **少于 32 字符启动失败**，生产必须用环境变量覆盖 |
| auth.token-ttl-hours | 12 | 过期返回 401，前端自动弹回登录页 |
| auth.default-admin-username / -password | admin / admin | 仅在 `sys_user` 为空表时由 `SeedUserInitializer` 兜底创建（**超级管理员**），等价于 `init.sql` 里的 `INSERT IGNORE`；不会覆盖谁改过的密码 |
| auth.seed-test-users | true | 是否补齐每个角色一个测试账号（`test_super` / `test_admin` / `test_user` / `test_guest`），只在用户名不存在时创建。**生产环境用 `AUTH_SEED_TEST_USERS=false` 关掉** |
| auth.test-user-password | test123456 | 测试账号的统一密码（入库前 BCrypt）。配置不合法时启动直接抛异常，不静默跳过 |
| spring.datasource.url / username | localhost:3306/chatbot / root | 有 `DB_URL` / `DB_USERNAME` 占位符，换环境不用改文件 |
| cors.allowed-origins | http://localhost:5173 | 跨域白名单，逗号分隔多个；**留空启动失败**。对外部署用 `CORS_ALLOWED_ORIGINS` 覆盖 |
| llm.base-url | 百炼 compatible-mode | 任何 OpenAI 兼容接口都能直连 |
| llm.api-key | 空 | 留空自动走 `MockLlmClient` |
| llm.model | qwen3.6-flash | **只是默认值**：请求体带 `model` 时以请求为准 |
| llm.available-models | qwen3.6-flash,qwen3.7-flash,qwen3.8-flash,qwen3.8-max | 界面可选模型白名单（逗号分隔，`LLM_AVAILABLE_MODELS` 可覆盖）。请求里的 `model` 不在里面就 400，错误文案里带上清单 |
| llm.vision-models | 同 available-models | available-models 的子集：能吃图片输入的模型（`LLM_VISION_MODELS` 可覆盖）。`GET /api/llm/options` 把它作为 `visionModels` 下发，前端靠它决定显不显示上传按钮 |
| spring.servlet.multipart.max-file-size / -request-size | 5MB / 6MB | 单张图上限 / 整个 multipart 请求上限（`UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE`）。超了是 **413**，由 `GlobalExceptionHandler` 接住给中文文案 |
| llm.enable-thinking | true | 映射为请求体顶层的 `enable_thinking`，取舍见下 |
| llm.request-timeout-seconds | 900 | 整轮生成的上限，**不是空闲超时**。SSE 超时自动取它 +30 秒 |
| llm.max-history-messages | 20 | 历史的**条数**上限，<=0 不限制；与 token 预算谁先满足谁生效 |
| llm.max-history-tokens | 24000 | 历史的 **token 预算**（估算：中文 1 字 1 token、其余 4 字符 1 token，思考也计入），超了从最老的开始截；<=0 不限 |
| spring.jpa.show-sql | false | 要调试 SQL 用 `SHOW_SQL=true` 启动 |

环境变量与配置项的对照表见 PROJECT_OVERVIEW.md 5.1。

### enable-thinking 的取舍

开启：回答质量更好，界面有「思考中…」反馈，而且思考期间一直有字节流动，不会被空闲超时掐断。
代价：思考内容按输出 token 计费——实测有过一轮思考 **2.4 万字、正式回答只有 1 个字**。
关闭：同一问题首字延迟从 **2.7 秒降到 0.4 秒**。

`request-timeout-seconds` 原来写死 5 分钟：推理模型思考超过 5 分钟会在第 300 秒被 JDK HttpClient 直接关流，
实测思考到 **31407 字、正式回答 0 字**，全部作废但思考 token 照样计费。

这里配的只是默认值：前端每条消息可以在请求体传 `"enableThinking": true/false` 单独开关。

**思考强度（`thinking_budget`）**：请求体可带正整数，限制思维链 token 上限；不传则用模型默认（qwen3.8 系默认 131072）。
界面档位 4096 / 16384 / 131072 对齐百炼 `reasoning_effort` 的 low / medium 映射；**`thinking_budget` 与 `reasoning_effort` 不能同时设置**（qwen3.8 系），我们只发前者。
超过某模型的「最大思维链长度」会返回 400 并在文案里写明上限（qwen3.6-flash 是 131072，qwen3.8 系是 262144）。

**联网搜索（`enable_search`）**：请求体带 `"enableSearch": true` 时后端下发 `enable_search: true`，模型可检索实时网页（天气 / 新闻 / 股价）。
三个边界：① OpenAI 兼容协议**拿不到搜索来源 / 角标**，引用 UI 做不了；② `qwen3.8-max` / `qwen3.8-flash` 在兼容协议下不支持 `search_strategy: agent`，我们固定用默认 turbo；
③ 计费约 turbo 3 元/千次 + 检索内容拼进提示词的输入 token，所以**界面默认关**。

**图片输入（多模态）**：带附件的消息在送给模型时，`content` 从字符串变成数组
`[{"type":"image_url","image_url":{"url":"data:<mime>;base64,..."}}..., {"type":"text","text":"..."}]`（图在前、文在后）。
字节以 base64 内联而不是给 URL：附件存在自己库里，没有可公网访问的地址。
三个已知边界：① 图片占的 token **不计入** `llm.max-history-tokens` 预算（估算器只认文字，视觉 token 由分辨率决定），
带图会话的真实上下文看用量里的 `prompt_tokens`（实测一张 320×200 的图约 90 token，且每轮历史都会重发）；
② 一个附件只属于一条消息（`linkToMessage` 带 `message_id IS NULL` 条件），重复提交同一个 id 会 400；
③ 2026-09-24 实测 qwen3.6/3.7/3.8-flash 与 qwen3.8-max 在兼容协议下都能正确读图。

**preserve_thinking**：qwen3.8-max / qwen3.8-flash 默认 true，要求历史 assistant 消息把 `reasoning_content` 完整回传、且不支持拼进 `content`。
我们把存库的思考随历史带上；缺了不报错，但多轮推理质量会打折。
换到不认识 `enable_thinking` 的服务商（OpenAI、DeepSeek 等）时，把 properties 里那行整行注释掉，
后端就不下发该参数，请求体回到改造之前的样子。

## 目录结构

    auth/        TokenService（签发/校验）、AuthInterceptor（拦截 + 回表查用户）、CurrentUser 及其
                 ArgumentResolver、RequireAdmin 注解、Roles 常量、AuthProperties
    controller/  REST 入口：Auth / Conversation / Chat / User / Attachment / Llm / AdminUser / AdminAudit
    service/     ChatService（SSE 编排、取消、历史截断、自动起标题、多模态历史拼装）、UserService（登录、改密码、改资料、统计）、
                 ConversationService（会话 CRUD + 消息分页 + 附件元信息分组）、AttachmentService（图片上传/校验/读取）、
                 AdminUserService（管理端单条增删改查 + 自我保护规则）、AdminUserBatchService（批量编排，逐条事务）、
                 AdminAuditService（审计写与读）
    repository/  Spring Data JPA：Conversation / Message / User / Attachment
    entity/      Conversation / Message / User / Attachment / Role
    dto/         请求与响应对象，含统一错误体 ErrorResponse、SSE 事件体 ChatEvent、附件元信息 AttachmentVO
    llm/         LlmClient 接口 + LlmStreamListener（思考/回答双通道回调）+ Mock 与 OpenAI 兼容实现 +
                 LlmContentPart（多模态 content 数组的元素）
    config/      WebConfig（CORS + 拦截器 + 参数解析器）、CorsProperties（CORS 白名单）、
                 AuthConfig（BCrypt）、LlmConfig、SeedUserInitializer（种子超管 + 超管自愈 + 测试账号）、GlobalExceptionHandler

## 后续待加

暂无硬性缺口。候选方向（都已在 PROJECT_OVERVIEW.md 十二节展开）：把 `attachment.data` 换成对象存储 key
（多实例部署的前提）、给「传了没发」的孤儿附件加定时清理、按 `usage` 回填做动态 token 预算（把图片 token 也算进去）。
管理台审计与批量操作已于 2026-09-25 落地（`/api/admin/audit` 与 `/api/admin/users/batch`）；
审计目前只覆盖用户管理的写操作，其他管理模块接入时各自调 `AdminAuditService.record()`。
