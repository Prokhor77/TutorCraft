# Changelog

## 0.1.0 — 2026-09-28
Первая сборка MVP по ТЗ v1.0 (гибрид LMS + SaaS для репетиторов, ADR-002). Статус и непроверенные части — `docs/ROADMAP.md`.
- Этап 0: монорепо, docker compose, CI, ADR-001…010, контракты REST (`docs/api/contract.md`) и событий Kafka.
- core-api (Java 21 / Spring Boot 3): identity (FR-AUTH-01..04, OAuth Google/Telegram), access (FR-ACL-01/02), audit (FR-REPORT-02),
  org, files (FR-CONTENT-04, видео), courses (FR-COURSE-01..07, FR-CONTENT-01..03), enrollment (FR-ENROL-01..06),
  assignments (FR-ASSIGN-01..07), gradebook (FR-GRADE-01..08), quiz + question bank (FR-QBANK, FR-QUIZ-01..07),
  progress (FR-PROG-01..05), forum (FR-FORUM-01..04), notifications + calendar (FR-NOTIF, FR-DASH-03), dashboard (FR-DASH-01/02),
  billing (FR-ENROL-09), integrations (FR-INTEG-01/02), демо-данные.
- media-worker (Go): HLS-транскодирование (FFmpeg). notifier (Go): email, Telegram-бот, WebSocket.
- web (Next.js 15): все экраны раздела 10 ТЗ + витрина/лендинг курса (SSR), дизайн-система TutorCraft Studio (Stitch).
