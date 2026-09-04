# Chatbot

A chatbot application with a Spring Boot backend and a Vue 3 frontend.

## Project Structure

| Directory       | Description                                        |
| --------------- | -------------------------------------------------- |
| chatbot/      | Backend: Spring Boot (Java 26, Maven, JPA, MySQL)  |
| chatbot-web/  | Frontend: Vue 3 + Vite + TypeScript                |

## Getting Started

### Backend

`ash
cd chatbot
./mvnw spring-boot:run
`

### Frontend

`ash
cd chatbot-web
npm install
npm run dev
`

## Configuration & Secrets

Real secrets (DB password, LLM API key) must NOT be committed. Put them in `chatbot/src/main/resources/application-local.properties` (git-ignored) and run with `--spring.profiles.active=local`, or set the `DB_PASSWORD` / `LLM_API_KEY` environment variables. With no API key configured, the backend falls back to a local mock LLM.

