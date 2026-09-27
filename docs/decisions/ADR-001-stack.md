# ADR-001: Технологический стек
Статус: принято · 2026-09-27

**Контекст.** ТЗ не фиксирует стек; команда определила его отдельно.

**Решение.**
- Web: Next.js 15 (App Router) + React 19 + TypeScript, Tailwind CSS, Radix UI-примитивы, TanStack Query (серверное состояние), Zustand (UI-состояние), next-intl (ru/en).
- Core API: Java 21 + Spring Boot 3.3 (Web, Security, Validation, JDBC, Data MongoDB, Data Redis, Kafka), Flyway, springdoc-openapi.
- Воркеры: Go 1.24 (media-worker, notifier), segmentio/kafka-go, minio-go, gorilla/websocket.
- Данные: PostgreSQL 16, MongoDB 7, Redis 7, S3 (MinIO локально).
- Брокер: **Apache Kafka** (KRaft, без ZooKeeper). Выбран вместо RabbitMQ: журнал событий с повторным чтением (реплей для новых консьюмеров, аналитики, вебхуков), партиционирование по `tenantId`.
- CDN: Cloudflare. Контейнеры: Docker + Compose; Kubernetes — позже.
- Мобильное приложение: React Native (P2, вне текущего объёма).

**Последствия.** Два языка бэкенда → два набора CI-проверок; контракт между ними — только события Kafka (`docs/events`).
