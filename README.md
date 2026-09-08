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

| Topic | PROJECT_OVERVIEW.md |
| ----- | ------------------- |
| Module-by-module architecture | 三、后端模块详解 / 四、前端模块详解 |
| Configuration & environment variables | 五、关键配置说明 |
| Database schema | 六、数据库设计 |
| Full API list | 七、API 接口汇总 |
| Chat & auth flows | 八、核心流程 |
| Security details (token format, password policy) | 九、安全设计要点 |
| Dev guide (backend / frontend / default admin) | 十、开发指南 |
| Extension points / known limitations | 十一、扩展点 / 十二、已知限制 |
