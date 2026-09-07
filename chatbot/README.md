# chatbot 后端

类 ChatGPT 网页版的 Spring Boot 后端（MVP）。
会话管理 + 消息存储 + SSE 流式对话，LLM 走百炼（OpenAI 兼容接口），未配 key 时自动用本地 Mock。

## 快速开始

1. 建库：在 MySQL 里执行 sql/init.sql（建 chatbot 库，表由 Hibernate 自动创建）
2. 填 src/main/resources/application.properties 里的 spring.datasource.username / password
3. （可选）填 llm.api-key（百炼 API Key）；留空则使用 Mock 模式
4. 启动：.\mvnw.cmd spring-boot:run （端口 8089）

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/conversations | 新建会话（初始标题「新的对话」） |
| GET | /api/conversations | 会话列表（按最近更新倒序） |
| GET | /api/conversations/{id}/messages | 历史消息（全量，无分页） |
| DELETE | /api/conversations/{id} | 删除会话（连带消息，一条 JPQL 批量删） |
| POST | /api/conversations/{id}/chat | 发消息，SSE 流式返回回复 |

会话标题：发出第一条消息时，自动用该消息前 30 字替换「新的对话」，前端侧边栏可以直接显示。

### chat 接口的 SSE 事件（JSON，null 字段不输出）

- {"type":"reasoning","content":"..."} —— 思考过程增量，出现在正式回答之前（仅推理模型 / llm.enable-thinking=true）
- {"type":"delta","content":"..."} —— 正式回答的增量文本，逐段推送
- {"type":"done","messageId":123} —— 生成结束，助手消息已入库
- {"type":"error","content":"..."} —— 出错，错误文案在 content（不是 message 字段）

reasoning 是过程展示，不属于助手消息正文，后端不入库：刷新页面只会看到 delta 拼出来的回答。
前端不认识这个 type 时忽略即可，delta / done / error 的老契约没变。
建议的用法是收到第一帧 reasoning 就显示「思考中…」，可折叠展示思考内容，收到第一帧 delta 再切到正文。

为什么要转发思考：推理模型可能先思考几十秒到几分钟才开始输出回答，
这段时间如果一帧都不发，浏览器 / Vite 代理 / Nginx 常见的 60 秒空闲超时会直接掐断连接，
那时回答还没开始，一个字都存不下来。实测转发思考后帧间隔最大 356ms，不会再触发空闲超时。

前端对接注意：这是 POST + SSE，浏览器原生 EventSource 只支持 GET，用不了；
要用 fetch + ReadableStream 自己按空行切帧解析，且 TextDecoder 必须开 stream:true（否则中文会被 chunk 切成乱码）。

### 停止生成

前端直接 abort 这个 fetch 即可，不需要额外接口。后端检测到连接断开会立刻停止调用模型，
已生成的部分照样入库，刷新页面能看到。思考阶段点停止同样立刻生效（思考增量也会检查取消标志），
但那时还没有正式回答，所以不会写入空的助手消息。
SSE 超时 = llm.request-timeout-seconds + 30 秒，走同一条逻辑。

### 错误响应体（非 SSE 接口）

统一结构，前端读 message 直接展示、读 status 做分支：

    {"timestamp":"...","status":404,"error":"Not Found","message":"会话不存在: 3","path":"/api/conversations/3/messages"}

由 GlobalExceptionHandler 加 server.error.include-message=always 保证。
chat 接口出错时返回的是上面的 SSE error 事件，不是这个结构。

### PowerShell 手动测试

    curl.exe -s -X POST http://localhost:8089/api/conversations
    curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H "Content-Type: application/json" -d '{"message":"你好"}'
    curl.exe -s -i http://localhost:8089/api/conversations/99999/messages
    curl.exe -s -i -X POST http://localhost:8089/api/conversations/1/chat -H "Content-Type: application/json" -d '{"message":""}'

## 配置

| 配置 | 默认 | 说明 |
|---|---|---|
| server.port | 8089 | |
| llm.base-url | 百炼 compatible-mode | OpenAI 兼容接口地址 |
| llm.api-key | 空 | 留空自动走 MockLlmClient |
| llm.model | qwen3.6-flash | |
| llm.enable-thinking | true | 思考开关，映射为请求体顶层的 enable_thinking。false 可把首字延迟降到秒级并省掉思考 token；整行注释掉则不下发该参数 |
| llm.request-timeout-seconds | 900 | 单次模型调用的总超时（整轮生成上限，不是空闲超时）。SSE 超时自动取它 +30 秒 |
| llm.max-history-messages | 20 | 每轮只把最近 N 条历史送给模型，<=0 表示不限制 |
| spring.jpa.show-sql | false | 要调试 SQL 用 SHOW_SQL=true 启动 |

思考开关的取舍：开启后回答质量更好、界面有「思考中…」反馈，但思考内容按输出 token 计费，
实测有过一轮思考 2.4 万字、正式回答只有 1 个字的情况；关闭后同一问题首字延迟从 2.7 秒降到 0.4 秒。
想临时关掉又不改仓库配置：在 application-local.properties 里写 llm.enable-thinking=false。

真实密钥放 application-local.properties（已 gitignore），启动加 --spring.profiles.active=local，
或用环境变量 DB_PASSWORD / LLM_API_KEY / LLM_ENABLE_THINKING / LLM_REQUEST_TIMEOUT。

## 目录结构

    controller/  REST 入口
    service/     业务逻辑（ChatService 负责 SSE 编排、取消、历史截断、自动起标题）
    repository/  Spring Data JPA
    entity/      Conversation / Message / Role
    dto/         请求与响应对象（含统一错误体 ErrorResponse）
    llm/         LlmClient 接口 + LlmStreamListener（思考/回答双通道回调）+ Mock 实现 + OpenAI 兼容实现
    config/      CORS、LLM 装配、全局异常处理

## 后续待加

用户体系、会话重命名接口、重新生成、token 用量统计、消息分页。
