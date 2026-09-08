# Chatbot

A full-stack AI chat app: Spring Boot backend + Vue 3 frontend, with multi-user auth,
multi-conversation management, and streaming (SSE) replies from Alibaba Cloud Bailian
DashScope or any other OpenAI-compatible model.

> 中文全量说明（架构 / 模块 / 数据库 / API / 安全设计 / 扩展点）见 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)。
> This README only covers how to get it running.

## Tech Stack

| Layer | Tech |
| ----- | ---- |
| Backend | Java 26, Spring Boot 4.1.1, Spring Data JPA, MySQL 8 |
| Frontend | Vue 3.5, TypeScript 6, Vite 8 |
| LLM | Bailian DashScope (OpenAI-compatible), streaming. No API key configured -> local mock LLM |

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
`local` profile, or export `DB_PASSWORD` / `LLM_API_KEY`. Full list of environment
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
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | 中文全量架构说明：模块清单、目录树、数据库设计、API 全量表、核心流程、安全设计、扩展点、已知限制 |
| [chatbot/README.md](chatbot/README.md) | Backend contract: SSE event shapes, why reasoning is forwarded, cancel semantics, error body, config trade-offs |
| [chatbot-web/README.md](chatbot-web/README.md) | Frontend conventions: Vite proxy, Node version, auth gate, SSE parsing over fetch, component map |

Startup commands live in this file only; contract details live in the module READMEs only.
Everything else points at them instead of repeating.
