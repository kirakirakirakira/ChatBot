# chatbot 后端

类 ChatGPT 网页版的 Spring Boot 后端：会话管理 + 消息存储 + SSE 流式对话 + Agent 只读工具调用 + AI 长期记忆 / 会话摘要 + 博客文章 + 自签 token 鉴权。
LLM 走百炼（OpenAI 兼容接口），未配 key 时自动用本地 Mock。

> 启动步骤见仓库根目录 [README.md](../README.md)，架构与逐文件清单见 [PROJECT_OVERVIEW.md](../PROJECT_OVERVIEW.md)。
> 本文只写**后端对外契约**和**读代码读不出来的配置取舍**。

## 接口

全量接口表见 PROJECT_OVERVIEW.md 第七节，这里只列几条契约前提：

- `/api/**` 除 `POST /api/auth/login` 外全部要 `Authorization: Bearer <token>`（白名单在 `WebConfig.PUBLIC_PATHS`）；新增接口默认受保护，不用改配置。
- 博客接口（`/api/blog/**`）全部要登录：已发布文章所有登录用户可读，写操作只认作者（别人的文章一律 404，不是 403）。列表不返回 `content`，详情才带正文；分页 `size` 默认 10、上限 100。发布状态由 `BlogPolicy` 决定，一期恒为 `PUBLISHED`，二期审批只改策略与加管理端接口。
- token 是自签 HMAC-SHA256、无状态：`auth.token-secret` 少于 32 字符后端直接启动失败；改密码后旧 token 立即失效（`iat` 与 `password_changed_at` 按毫秒比对）。
- **会话按用户隔离**：查不到或不属于自己的会话一律 404 而不是 403——403 会泄露「这个 id 存在」，而 id 是自增的，等于让人枚举出全站有多少会话。
- **消息接口是游标分页**：`GET /api/conversations/{id}/messages` 返回 `{items, beforeId, hasMore}`，`before`（id 游标）与 `limit`（默认 50、上限 200）走 query。用游标不用 offset：边翻页边有新消息入库时 offset 会重复或漏消息。
- **图片走两步**：先 `POST /api/conversations/{id}/attachments`（multipart 字段 `file`）拿附件 id，再放进 `ChatRequest.attachmentIds`。读取 `GET /api/attachments/{id}` 要登录且按归属校验（404 口径同会话），所以前端只能 fetch 成 blob 转 objectURL，不能用 `<img src>`，也没有 `?token=` 兜底。单张 ≤5MB、每条 ≤4 张、MIME 白名单 5 种位图（无 SVG）。
- **每轮注入「当前日期」**：第一条 system 消息 =「当前日期：YYYY-MM-DD（时区 `Asia/Shanghai`）」+ 用户人设，合成一条。模型没有时钟，不注入就只能猜；不新增第二条 system（部分服务商只认第一条）。
- **人设只属于自己**：管理员视角的 `AdminUserVO` 连 `systemPrompt` 字段都没有。人设每轮计入输入 token，写多长就烧多少输入钱。
- **管理端全在 `/api/admin/**`**，类级 `@RequireAdmin`：列表 / 字典 / 建号 / 改角色 / 启停 / 重置密码 / 强制下线 / 删号。自我保护规则一律 400 中文文案；删号级联硬删（附件→消息→会话→用户），不做软删除。
- **管理端写操作有审计**：六个写操作成功后各写一行 `admin_audit_log`，与业务同事务（回滚则不留痕）；读取走 `GET /api/admin/audit`。审计表不建外键、不开删除入口。
- **角色是层级模型**：超管 > 管理员 > 普通用户 > 访客，管理操作只能「上对下」，谁都不能改自己的角色。细节见 PROJECT_OVERVIEW.md 8.6。
- **「管自己」的接口有五个**：改密码、改人设、改资料、使用统计、退出所有设备。另有 AI 记忆六个接口（五个记忆 CRUD + 一个总开关），全部固定 `/api/users/me/memories` / `/api/users/me/memory-settings`，不接受 userId 参数。
- **Agent 工具调用**：当前 3 个只读工具 `search_blog` / `get_blog_post` / `calculator`。planner 非流式决定是否调用，工具由后端白名单执行，最终回答仍走 SSE 流式；不会自动改数据。登录名与角色不可自改（username 是审计锚点，role / status 是管理端写口径）；改资料不换 token，改密码 / revoke 会作废旧登录态。
- **带图请求打到不支持图片的模型会 400**（`llm.vision-models` 白名单），错误文案列出可用模型；校验刻意放在用户消息落库之前。

会话标题：发出第一条消息时，自动用该消息前 30 字替换「新的对话」。

## SSE 事件契约（POST /api/conversations/{id}/chat）

JSON，null 字段不输出：

| 事件 | 形状 | 说明 |
|---|---|---|
| reasoning | `{"type":"reasoning","content":"..."}` | 思考增量，出现在正式回答之前；仅推理模型且开启思考时出现 |
| delta | `{"type":"delta","content":"..."}` | 正式回答的增量文本 |
| done | `{"type":"done","messageId":123,"model":"...","prompt_tokens":4,"completion_tokens":30,"reasoning_tokens":12}` | 生成结束，助手消息已入库，并带回 model 与用量 |
| error | `{"type":"error","content":"..."}` | 出错。**文案在 `content`，不是 `message`** |

`reasoning` 只是过程展示，不属于正文：正文永远是 delta 拼出来的回答。思考全文在生成结束时随助手消息入库（`message.reasoning`），刷新后折叠块还能重读；没开思考时该列为 NULL、也不下发。前端不认识某个 `type` 直接忽略即可。

`POST /api/conversations/{id}/regenerate` **复用同一套事件**：先删最后一条助手消息，再用它前面那条用户消息重跑；最后一条不是助手消息或会话为空返回 400。

### 为什么必须转发思考

推理模型可能先思考几十秒到几分钟才输出回答。这段时间一帧不发，浏览器 / Vite / Nginx 常见的 60 秒空闲超时会直接掐断连接，回答一个字都存不下来。实测转发思考后帧间隔最大 356ms，不再触发空闲超时。

### 停止生成

前端直接 abort 那个 fetch 即可，**不需要额外接口**。后端检测到连接断开就停止调用模型，已生成部分照样入库，刷新可见；思考阶段点停止同样立刻生效，但那时还没有正式回答，所以不会写入空的助手消息。SSE 超时 = `llm.request-timeout-seconds` + 30 秒。

前端怎么解析这个流见 [chatbot-web/README.md](../chatbot-web/README.md)。

## 错误响应体（非 SSE 接口）

统一结构，前端读 `message` 直接展示、读 `status` 做分支：

    {"timestamp":"...","status":404,"error":"Not Found","message":"会话不存在: 3","path":"/api/conversations/3/messages"}

由 `GlobalExceptionHandler` 自己组装 `ErrorResponse`，**不依赖 `server.error.include-message`**（`application.properties` 里没有这一项）。每个 handler 显式设 `Content-Type: application/json`，否则会因 `Accept: text/event-stream` 触发内容协商 406，把真实状态码盖掉。chat 接口出错返回的是上面的 SSE `error` 事件，不是这个结构。

账号被**禁用**时两个状态码用同一句文案「账号已被禁用，请联系管理员」：已登录的下一个请求 401（拦截器每请求回表，立即生效），登录接口 403 且**放在密码校验之后**（顺序反了就成了可枚举信息）。`must_change_password=1` 的账号登录拿到 `mustChangePassword=true`，前端据此强制改密。

## 手动测试

所有接口先登录换 token：

```powershell
$tok = (curl.exe -s -X POST http://localhost:8089/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}' | ConvertFrom-Json).token
$h = "Authorization: Bearer $tok"

curl.exe -s -X POST http://localhost:8089/api/conversations -H $h
curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":"你好"}'
curl.exe -s "http://localhost:8089/api/conversations/1/messages?limit=5" -H $h      # 分页：{items, beforeId, hasMore}
curl.exe -s -X PUT http://localhost:8089/api/conversations/1/title -H $h -H "Content-Type: application/json" -d '{"title":"新名字"}'
curl.exe -N -X POST http://localhost:8089/api/conversations/1/regenerate -H $h -H "Content-Type: application/json" -d '{}'
curl.exe -s http://localhost:8089/api/llm/options -H $h
curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":"你好","model":"qwen3.8-max","thinkingBudget":16384}'
curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":"今天上海天气","enableSearch":true}'
curl.exe -s -i http://localhost:8089/api/conversations/99999/messages -H $h         # 不存在 / 不是自己的：404
curl.exe -s "http://localhost:8089/api/admin/users?page=0&size=20" -H $h            # 管理端接口要管理员 token
curl.exe -s "http://localhost:8089/api/admin/audit?page=0&size=20" -H $h
curl.exe -s -X POST http://localhost:8089/api/blog/posts -H $h -H "Content-Type: application/json" -d '{"title":"测试文章","summary":"","content":"正文 **Markdown**"}'
curl.exe -s "http://localhost:8089/api/blog/posts?page=0&size=10" -H $h        # 已发布列表（不含 content）
curl.exe -s "http://localhost:8089/api/blog/mine?page=0&size=10" -H $h          # 我的全部状态
curl.exe -s -i -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":""}'
```

不带 token 的请求全部 401：拦截器跑在 `@Valid` 和控制器之前，所以想看 404 错误体或 400 校验错误，必须先带 token。管理端接口还要管理员 token，普通用户一律 403「需要管理员权限」。

## AI 记忆与会话摘要

- **记忆**：跨会话、用户可见可编辑。`user_memory` 按 `(user_id, content_hash)` 去重；`source=MANUAL` 的记录不会被自动抽取覆盖。
- **摘要**：单会话、内部使用。`conversation.summary` + `summary_up_to_message_id` 表示摘要覆盖到哪条消息；拼历史时 `id <= 该值` 的消息不再重复发给模型。
- **读路径**：`ChatService.recentHistory()` 取 ACTIVE 记忆（默认最多 20 条 / 1200 token）和会话摘要，与日期、用户人设**合成一条 system**。部分服务商只认第一条 system，所以不要再拆成第二条。
- **写路径**：SSE `done` 之后才异步提交记忆抽取与摘要。LLM 调用不包数据库事务；抽取 / 摘要失败只记日志，不影响已经完成的回答。
- **开关**：`memory.enabled` 是全局总开关，关闭后记忆和会话摘要一起停；`sys_user.memory_enabled` 只关个人记忆，抽取/注入记忆停止，但会话摘要仍可运行（它只压缩当前会话，不跨会话学习）。Mock 模式（未配 API Key）自动跳过后台 LLM 调用，手动记忆仍可用。
- **隐私**：记忆接口全部只认 token 里的当前用户；管理员没有查看别人记忆的接口。入库前过滤明显密钥 / 身份证 / 银行卡；用户可以编辑、删除、清空。

## 配置

全量配置项与环境变量对照见 PROJECT_OVERVIEW.md 5.1，这里只留需要解释的：

| 配置 | 默认 | 说明 |
|---|---|---|
| auth.token-secret | dev 占位值 | **少于 32 字符启动失败**，生产必须用环境变量覆盖 |
| llm.api-key | 空 | 从 `DASHSCOPE_API_KEY`（兼容 `LLM_API_KEY`）读取；留空自动走 `MockLlmClient` |
| llm.available-models | qwen3.8-flash,qwen3.8-max,deepseek-v4.1-flash | 界面可选模型白名单；请求里的 `model` 不在里面就 400，并把清单写进错误文案 |
| llm.vision-models | 同 available-models（当前三个模型都支持图片） | 能吃图片输入的模型子集；`GET /api/llm/options` 下发 `visionModels`，前端靠它决定显不显示上传按钮 |
| llm.enable-thinking | true | 映射为请求体顶层 `enable_thinking`；服务商不认识该字段就整行注释掉，取舍见下 |
| llm.request-timeout-seconds | 900 | 整轮生成上限，**不是空闲超时**；SSE 超时自动取它 +30 秒 |
| llm.max-history-messages / tokens | 20 / 24000 | 历史的条数上限与 token 预算，谁先满足谁生效；<=0 不限 |
| memory.enabled | true | AI 记忆总开关；false 时不抽取、不注入，手动记忆保留 |
| memory.extraction-model / summary-model | qwen3.8-flash | 后台抽取 / 摘要模型；不在白名单时回落 llm.model |
| memory.max-per-turn / max-active | 3 / 100 | 每轮最多应用几条抽取结果 / 单用户最多保留多少条 |
| memory.inject-limit / inject-max-tokens | 20 / 1200 | 每轮注入记忆的条数与 token 上限 |
| memory.summary-trigger-messages / -tokens | 20 / 8000 | 未摘要消息达到任一阈值触发摘要 |
| memory.summary-batch-messages / -max-chars | 20 / 800 | 每次压缩多少条旧消息 / 摘要最多多少字符 |
| memory.min-confidence | 0.70 | 自动抽取最低置信度 |
| agent.enabled | true | Agent 总开关；false 完全退回普通聊天 |
| agent.planner-model | qwen3.8-flash | planner 模型；不在白名单时回落 llm.model |
| agent.max-steps | 5 | 最多几轮工具调用 |
| agent.tool-timeout-seconds | 20 | 单工具执行超时 |
| agent.max-result-chars | 4000 | 回灌给模型的单结果上限 |
| agent.planner-max-tokens | 512 | planner 输出上限 |

### enable-thinking 的取舍

开启：回答质量更好，界面有「思考中…」反馈，思考期间一直有字节流动，不会被空闲超时掐断。
代价：思考按输出 token 计费——实测有过一轮思考 **2.4 万字、正式回答 1 个字**。
关闭：同一问题首字延迟从 **2.7 秒降到 0.4 秒**。

前端每条消息可以在请求体传 `"enableThinking": true/false` 单独开关。

**思考强度（`thinking_budget`）**：请求体可带正整数限制思维链 token 上限，不传则用模型默认（qwen3.8 系 131072）。界面档位 4096 / 16384 / 131072 对齐 `reasoning_effort` 的 low / medium；**`thinking_budget` 与 `reasoning_effort` 不能同时设置**，我们只发前者。超过模型上限返回 400 并写明上限。

**联网搜索（`enable_search`）**：请求体带 `"enableSearch": true` 时下发 `enable_search: true`。三个边界：① 兼容协议拿不到搜索来源 / 角标，做不了引用 UI；② qwen3.8-max / flash 在兼容协议下不支持 `search_strategy: agent`，固定 turbo；③ 约 3 元/千次 + 检索内容计入输入 token，所以界面默认关。

**图片输入**：带附件的消息送给模型时 `content` 变成数组 `[{"type":"image_url","image_url":{"url":"data:<mime>;base64,..."}}, {"type":"text","text":"..."}]`（图在前、文在后）。字节以 base64 内联而不是给 URL：附件在自己库里，没有公网地址。已知边界：① 图片 token **不计入** `llm.max-history-tokens` 预算，真实上下文看用量里的 `prompt_tokens`（实测一张 320×200 约 90 token，且每轮历史都重发）；② 一个附件只属于一条消息，重复提交同一 id 会 400。

**preserve_thinking**：qwen3.8-max / flash 默认 true，要求历史 assistant 消息把 `reasoning_content` 完整回传且不能拼进 `content`，我们随历史带上；缺了不报错但多轮推理质量会打折。

## 目录结构

    auth/        TokenService、AuthInterceptor、CurrentUser 及其 ArgumentResolver、RequireAdmin、Roles、AuthProperties
    controller/  REST 入口：Auth / Conversation / Chat / User / Attachment / Llm / AdminUser / AdminAudit / Blog
    service/     Chat（SSE 编排 / 取消 / 历史截断 / 自动标题 / 多模态 / 记忆与摘要注入 / Agent 接入）、Agent（planner 循环）、AgentToolCallStore、Conversation、User、Attachment、Memory、ConversationSummary + Store、LlmText、Blog、BlogPolicy、
                 AdminUser（单条 + 自我保护）、AdminUserBatch（批量编排）、AdminAudit
    repository/  Spring Data JPA：Conversation / Message / User / Attachment / BlogPost / UserMemory / AgentToolCall
    entity/      Conversation / Message / User / Attachment / Role / AdminAuditLog / BlogPost / PostStatus / UserMemory / MemoryKind / MemorySource / MemoryStatus / AgentToolCall
    dto/         请求与响应对象，含 ErrorResponse、ChatEvent、AttachmentVO、分页与管理端 VO、博客 BlogPostVO/BlogPostSummaryVO/BlogPostSummaryView/SaveBlogPostRequest
    llm/         LlmClient + LlmStreamListener + Mock 与 OpenAI 兼容实现 + LlmContentPart
    config/      WebConfig（CORS + 拦截器 + 参数解析器）、CorsProperties、AuthConfig、LlmConfig、
                 SeedUserInitializer、GlobalExceptionHandler

## 后续待加

暂无硬性缺口。候选方向（均见 PROJECT_OVERVIEW.md 十二节）：把 `attachment.data` 换成对象存储 key（多实例部署前提）、给「传了没发」的孤儿附件加定时清理、按 `usage` 回填做动态 token 预算（把图片 token 也算进去）、记忆量上来后给 `user_memory` 加 embedding 做语义召回、多实例部署时把摘要的进程内锁换成数据库行锁、Agent 二期写工具 + 审批 + MCP、博客二期接审批。

审计目前只覆盖用户管理的写操作，其他管理模块接入时各自调 `AdminAuditService.record()`。
