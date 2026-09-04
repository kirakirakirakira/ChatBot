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
| POST | /api/conversations | 新建会话 |
| GET | /api/conversations | 会话列表（按最近更新倒序） |
| GET | /api/conversations/{id}/messages | 历史消息 |
| DELETE | /api/conversations/{id} | 删除会话 |
| POST | /api/conversations/{id}/chat | 发消息，SSE 流式返回回复 |

### chat 接口的 SSE 事件（JSON）

- {"type":"delta","content":"..."} —— 增量文本，逐段推送
- {"type":"done","messageId":123} —— 生成结束，助手消息已入库
- {"type":"error","message":"..."} —— 出错

### PowerShell 手动测试

    curl.exe -s -X POST http://localhost:8089/api/conversations
    curl.exe -N -X POST http://localhost:8089/api/conversations/1/chat -H "Content-Type: application/json" -d '{"message":"你好"}'

## 目录结构

    controller/  REST 入口
    service/     业务逻辑（ChatService 负责 SSE 流式编排）
    repository/  Spring Data JPA
    entity/      Conversation / Message / Role
    dto/         请求与响应对象
    llm/         LlmClient 接口 + Mock 实现 + OpenAI 兼容实现
    config/      CORS、LLM 装配

## 后续待加

用户体系、会话自动起标题、重新生成 / 停止生成、token 用量统计。
