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
- **人设只属于自己**：`PUT /api/users/me/system-prompt` 改的是 token 里那个用户；管理员 `GET /api/users` 拿到的列表里 `systemPrompt` 恒为 null。
  人设每轮作为 system 消息放在历史最前面，**计入输入 token**——写两千人设每轮就烧两千字输入钱。
- **会话按用户隔离**：`conversation.owner_id` 记录归属，会话相关接口的控制器都接 `CurrentUser`，校验统一在 service 层。
  查不到或不是自己的会话**一律 404，不返回 403**——403 会把「这个 id 确实存在」泄露出去，而 id 是自增的，
  等于让人枚举出全站有多少会话。管理员也没有跨用户特权。
- **消息接口是分页的**：`GET /api/conversations/{id}/messages` 返回 `{items, beforeId, hasMore}` 而不是数组，
  `before`（id 游标）和 `limit`（默认 50、上限 200）走 query 参数。用游标不用 offset：
  一边翻页一边有新消息入库时，offset 分页会重复或漏消息。

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
curl.exe -s -i -X POST http://localhost:8089/api/conversations/1/chat -H $h -H "Content-Type: application/json" -d '{"message":""}'
```

不带 token 时这四条全部返回 401：拦截器跑在 `@Valid` 和控制器之前，
所以后两条想看到 404 错误体和 400 校验错误，必须先带上 token。

## 配置

| 配置 | 默认 | 说明 |
|---|---|---|
| server.port | 8089 | |
| auth.token-secret | dev 占位值 | **少于 32 字符启动失败**，生产必须用环境变量覆盖 |
| auth.token-ttl-hours | 12 | 过期返回 401，前端自动弹回登录页 |
| auth.default-admin-username / -password | admin / admin | 仅在 `sys_user` 为空表时由 `AdminUserInitializer` 兜底创建，等价于 `init.sql` 里的 `INSERT IGNORE`；不会覆盖谁改过的密码 |
| spring.datasource.url / username | localhost:3306/chatbot / root | 有 `DB_URL` / `DB_USERNAME` 占位符，换环境不用改文件 |
| cors.allowed-origins | http://localhost:5173 | 跨域白名单，逗号分隔多个；**留空启动失败**。对外部署用 `CORS_ALLOWED_ORIGINS` 覆盖 |
| llm.base-url | 百炼 compatible-mode | 任何 OpenAI 兼容接口都能直连 |
| llm.api-key | 空 | 留空自动走 `MockLlmClient` |
| llm.model | qwen3.6-flash | **只是默认值**：请求体带 `model` 时以请求为准 |
| llm.available-models | qwen3.6-flash,qwen3.7-flash,qwen3.8-flash,qwen3.8-max | 界面可选模型白名单（逗号分隔，`LLM_AVAILABLE_MODELS` 可覆盖）。请求里的 `model` 不在里面就 400，错误文案里带上清单 |
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

**preserve_thinking**：qwen3.8-max / qwen3.8-flash 默认 true，要求历史 assistant 消息把 `reasoning_content` 完整回传、且不支持拼进 `content`。
我们把存库的思考随历史带上；缺了不报错，但多轮推理质量会打折。
换到不认识 `enable_thinking` 的服务商（OpenAI、DeepSeek 等）时，把 properties 里那行整行注释掉，
后端就不下发该参数，请求体回到改造之前的样子。

## 目录结构

    auth/        TokenService（签发/校验）、AuthInterceptor（拦截 + 回表查用户）、CurrentUser 及其
                 ArgumentResolver、RequireAdmin 注解、Roles 常量、AuthProperties
    controller/  REST 入口：Auth / Conversation / Chat / User
    service/     ChatService（SSE 编排、取消、历史截断、自动起标题）、UserService（登录、改密码）、ConversationService
    repository/  Spring Data JPA：Conversation / Message / User
    entity/      Conversation / Message / User / Role
    dto/         请求与响应对象，含统一错误体 ErrorResponse、SSE 事件体 ChatEvent
    llm/         LlmClient 接口 + LlmStreamListener（思考/回答双通道回调）+ Mock 与 OpenAI 兼容实现
    config/      WebConfig（CORS + 拦截器 + 参数解析器）、CorsProperties（CORS 白名单）、
                 AuthConfig（BCrypt）、LlmConfig、AdminUserInitializer、GlobalExceptionHandler

## 后续待加

会话重命名接口、暂无。会话重命名、重新生成、token 用量统计均已于 2026-09-24 完成。