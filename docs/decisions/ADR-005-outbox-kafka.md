# ADR-005: Доменные события через outbox → Kafka
Статус: принято

Топики: `tc.<context>.<event>.v1`, ключ — `tenantId`. Конверт события: `{eventId, type, occurredAt, tenantId, payload}` (схемы в `docs/events`). Внутри монолита модули подписываются на события через Spring `ApplicationEventPublisher` (синхронно в транзакции только для чтения-моделей; побочные эффекты — через outbox).
