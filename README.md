# Chatbot

A full-stack AI chat app: Spring Boot backend + Vue 3 frontend, with multi-user auth,
multi-conversation management, and streaming (SSE) replies from Alibaba Cloud Bailian
DashScope or any other OpenAI-compatible model.

> 中文全量说明（架构 / 逐文件清单 / 数据库 / API 全量表 / 核心流程 / 安全设计 / 扩展点 / 已知限制）见 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)。
> This README only covers how to get it running.

> **Scope note / 范围说明**: "multi-user" means login, user management, **and per-user conversation data**.
> Every conversation belongs to exactly one user (`conversation.owner_id`); listing, reading, deleting and
> chatting are all scoped to the owner, and cross-user access returns 404 — for admins too.
> 「多用户」= 登录 + 用户管理 + 会话数据隔离：每个会话归属一个用户，越权访问（含管理员跨用户）一律 404。见 PROJECT_OVERVIEW.md 第九节。

## Tech Stack

| Layer | Tech |
| ----- | ---- |
| Backend | Java 26, Spring Boot 4.1.1, Spring Data JPA, MySQL 8 |
| Frontend | Vue 3.5, TypeScript 6, Vite 8, markdown-it + highlight.js + DOMPurify (assistant-message rendering) |
| LLM | Bailian DashScope (OpenAI-compatible), streaming, per-request model selection (`llm.available-models`) and thinking budget. No API key configured -> local mock LLM |

## Project Structure

| Directory | Description |
| --------- | ----------- |
| `chatbot/` | Backend: Spring Boot, Maven |
| `chatbot-web/` | Frontend: Vue 3 + Vite + TypeScript |

## Getting Started

### Backend

```bash
cd chatbot
./mvnw spring-boot:run          # http://localhost:8089
```

On Windows, use the local profile so that `application-local.properties` is picked up:

```powershell
cd chatbot
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

Needs a MySQL `chatbot` database, or just let `spring.jpa.hibernate.ddl-auto=update` create
the tables on first boot.

Real secrets (DB password, LLM API key) must NOT be committed. Put them in
`chatbot/src/main/resources/application-local.properties` (git-ignored) and run with the
`local` profile, or export `DB_PASSWORD` / `LLM_API_KEY` / `DB_URL` / `DB_USERNAME` /
`CORS_ALLOWED_ORIGINS`. Full list of environment
variables: PROJECT_OVERVIEW.md, section 5.1.

### Frontend

```bash
cd chatbot-web
npm install
npm run dev                     # http://localhost:5173, Vite proxies /api to :8089
```

### Default admin

`admin` / `admin` - change the password right after the first login.

## Documentation Map

| Read this | For |
| --------- | --- |
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | **架构事实源（改代码前先读它）**：五分钟速览与检索速查、目录树 + 逐文件清单、后端分层与调用链、前端组件与状态、配置与环境变量全表、列级数据库设计、API 全量表与 JSON 形状、五条核心流程、安全设计、开发命令、**改完代码后的文档同步清单**、扩展点、已知限制与 18 条踩坑记录 |
| [chatbot/README.md](chatbot/README.md) | Backend contract: SSE event shapes, why reasoning is forwarded, cancel semantics, error body, config trade-offs |
| [chatbot-web/README.md](chatbot-web/README.md) | Frontend conventions: Vite proxy, Node version, auth gate, SSE parsing over fetch, component map |

Startup commands live in this file only; contract details live in the module READMEs only.
Everything else points at them instead of repeating.

### For AI agents / 给大模型与 AI 编码助手

仓库根目录的 `AGENTS.md` 是协作规则（**检索代码前先读 `PROJECT_OVERVIEW.md`；每次改完代码同步更新文档**）。
注意它被 `.gitignore` 忽略、只在本地工作区存在；如果你在一份新克隆的仓库里看不到它，
请直接以 `PROJECT_OVERVIEW.md` 为准——它入库，且第十一节就是「代码修改后的文档同步清单」。
