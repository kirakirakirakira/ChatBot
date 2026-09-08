# Chatbot

A chatbot application with a Spring Boot backend and a Vue 3 frontend.

## Project Structure

| Directory       | Description                                        |
| --------------- | -------------------------------------------------- |
| chatbot/      | Backend: Spring Boot (Java 26, Maven, JPA, MySQL)  |
| chatbot-web/  | Frontend: Vue 3 + Vite + TypeScript                |

## Getting Started

### Backend

```bash
cd chatbot
./mvnw spring-boot:run
```

### Frontend

```bash
cd chatbot-web
npm install
npm run dev
```

## Authentication

Every `/api/**` endpoint except `POST /api/auth/login` needs a token. The frontend treats any
`401` as "session gone" and switches back to the login page, so there is no page you can reach
without logging in first.

### Users (`sys_user`)

DDL and seed data live in `chatbot/sql/init.sql`, mirrored by the JPA entity
`chatbot/src/main/java/com/chatbot/chatbot/entity/User.java`.

| Column                | Description                                                                                                        |
| --------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `username`            | Login name, unique (`uk_sys_user_username`)                                                                          |
| `password`            | BCrypt hash (cost 10). No endpoint ever returns it                                                                   |
| `role`                | `0` = normal user, `1` = admin. The constants live in `auth/Roles.java`; admin-only methods carry `@RequireAdmin`      |
| `created_at`          | `datetime(6)`                                                                                                        |
| `password_changed_at` | `datetime(6)`, `NULL` until the first change. Tokens issued before it are rejected, so changing a password kills every other session |

Default admin: `admin` / `admin`. `init.sql` seeds it with `INSERT IGNORE`, so the script is
re-runnable and never overwrites a password someone already changed; `config/AdminUserInitializer.java`
re-creates the account on startup if the table is empty. Override with `AUTH_ADMIN_USERNAME` /
`AUTH_ADMIN_PASSWORD`.

### Endpoints

| Method | Path                     | Access                     | Body / Result                                                                             |
| ------ | ------------------------ | -------------------------- | ------------------------------------------------------------------------------------------ |
| `POST` | `/api/auth/login`        | public                     | `{username, password}` -> `{token, tokenType, expiresIn, user}`                              |
| `GET`  | `/api/auth/me`           | logged in                  | Current user; the frontend calls it on startup to restore the session                        |
| `PUT`  | `/api/users/me/password` | logged in                  | `{oldPassword, newPassword}` -> a fresh `LoginResponse`, so the current session survives      |
| `GET`  | `/api/users`             | admin only (`@RequireAdmin`)| User list; normal users get `403`                                                            |

Wrong username and wrong password return the same `401` message, so a login failure does not leak
which accounts exist. New passwords must be 6-64 characters and different from the old one;
`oldPassword` has no length rule because the seeded admin password is only 5 characters long.

Tokens are stateless `base64url(payloadJson).base64url(HMAC-SHA256)`, signed with `auth.token-secret`
(`AUTH_TOKEN_SECRET`, at least 32 characters or the backend refuses to start) and valid for
`auth.token-ttl-hours` (default 12). Send them as `Authorization: Bearer <token>`. The payload carries
`iat` in epoch **milliseconds** so that a token issued in the same second as a password change can
still be told apart from the replacement token.

Frontend: `chatbot-web/src/auth.ts` keeps the token and user in `localStorage`; `App.vue` renders
`LoginView.vue` until `/api/auth/me` succeeds, then `ChatView.vue`. The top bar offers
`ChangePasswordDialog.vue` for everyone and `UserListDialog.vue` for admins only.

`chatbot/test_sse.py` logs in before calling the API; pass `--user` / `--password` for any account
other than `admin`.

## Configuration & Secrets

Real secrets (DB password, LLM API key) must NOT be committed. Put them in `chatbot/src/main/resources/application-local.properties` (git-ignored) and run with `--spring.profiles.active=local`, or set the `DB_PASSWORD` / `LLM_API_KEY` environment variables. With no API key configured, the backend falls back to a local mock LLM.

