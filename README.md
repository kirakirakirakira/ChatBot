# Chatbot

类 ChatGPT 的全栈 AI 聊天应用：Spring Boot 后端 + Vue 3 前端，支持多用户登录、多会话管理与 SSE 流式对话。
模型走阿里云百炼 DashScope（OpenAI 兼容接口），也可直连任何 OpenAI 兼容服务；未配 API Key 时自动降级为本地 Mock。

> 架构 / 逐文件清单 / 数据库 / API 全表 / 核心流程 / 安全设计 / 已知限制见 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)；
> 本文只负责「怎么跑起来」。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 26、Spring Boot 4.1.1、Spring Data JPA、MySQL 8 |
| 前端 | Vue 3.5、TypeScript 6、Vite 8、vue-router 4、markdown-it + highlight.js + DOMPurify |
| LLM | 百炼 DashScope（OpenAI 兼容）：流式输出、按请求选模型、思考预算、可选联网搜索、图片输入 |

## 目录结构

| 目录 | 说明 |
| --- | --- |
| `chatbot/` | 后端：Spring Boot + Maven，端口 8089 |
| `chatbot-web/` | 前端：Vue 3 + Vite + TypeScript，端口 5173，`/api` 代理到 8089 |

## 快速开始

### 后端

```bash
cd chatbot
./mvnw spring-boot:run          # http://localhost:8089
```

Windows 下用 local profile 才会加载 `application-local.properties`：

```powershell
cd chatbot
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
```

需要本机 MySQL（库名 `chatbot`）；不想手工建表就直接启动，`ddl-auto=update` 会建表、`SeedUserInitializer` 会建默认账号。
真实密钥不要提交：DB 密码放 `chatbot/src/main/resources/application-local.properties`（已 gitignore），百炼 API Key 用环境变量
`DASHSCOPE_API_KEY`（兼容旧的 `LLM_API_KEY`）；其余环境变量见 PROJECT_OVERVIEW.md 5.1。**不配 Key 也能跑**，会自动走 Mock。

### 前端

```bash
cd chatbot-web
npm install
npm run dev                     # http://localhost:5173
```

前端是 history 模式路由（`/chat`、`/admin/users`…），开发期 Vite 自带回退；**生产部署要让静态服务器把未命中路径回退到
`index.html`**（nginx：`try_files $uri /index.html`），否则刷新深层 URL 会 404。

### 默认账号

`admin` / `admin`（**超级管理员**，首次登录后请立刻改密码）。为验证角色层级，后端还会在启动时按用户名补齐四个测试账号
（存在即不动，不会覆盖或重置改过的密码）：

| 用户名 | 角色 | 密码 |
| --- | --- | --- |
| `test_super` | 超级管理员 | `test123456` |
| `test_admin` | 管理员 | `test123456` |
| `test_user` | 普通用户 | `test123456` |
| `test_guest` | 访客 | `test123456` |

生产环境用 `AUTH_SEED_TEST_USERS=false` 关掉。老库若种子账号还是普通管理员，启动新版后端时只要系统里没有启用的超管，就会自动提回超管。

## 文档地图

| 文档 | 内容 |
| --- | --- |
| [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md) | **架构事实源（改代码前先读）**：速览与检索速查、目录树与逐文件清单、分层与调用链、配置与环境变量、数据库、API 全表、核心流程、安全设计、开发命令、文档同步清单、扩展点、已知限制与踩坑 |
| [chatbot/README.md](chatbot/README.md) | 后端契约：SSE 事件形状、思考转发、取消语义、错误响应体、关键配置取舍 |
| [chatbot-web/README.md](chatbot-web/README.md) | 前端约定：Vite 代理、Node 版本、登录闸门、SSE 解析、组件地图 |

启动步骤只写在本文件；契约细节只在对应模块 README。

### 给大模型 / AI 编码助手

仓库根 `AGENTS.md` 是协作规则（检索代码前先读 `PROJECT_OVERVIEW.md`、改完代码同步文档），但它被 `.gitignore` 忽略、
只在本地工作区存在；新克隆的仓库里看不到它时，直接以 `PROJECT_OVERVIEW.md` 为准——它入库，第十一节就是文档同步清单。
