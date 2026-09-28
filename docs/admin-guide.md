# Руководство администратора

## Установка
См. `README.md` (docker compose). Для продакшна: те же образы, внешние управляемые PostgreSQL 16, MongoDB 7 (replica set), Redis 7, Kafka 3.8 (KRaft), S3-совместимое хранилище; перед web и бакетом `hls/` — CDN (Cloudflare).

## Конфигурация
Все параметры — переменные окружения (`.env.example` — полный список с комментариями). Приложение не стартует с некорректной конфигурацией (ARCH-05). Обязательные секреты: `JWT_SECRET` (≥ 32 символов, общий для core-api и notifier), `DATA_ENCRYPTION_KEY`, пароли БД/Redis/S3. В продакшне `COOKIE_SECURE=true`, `SPRING_PROFILES_ACTIVE=prod`, `SEED_DEMO_DATA=false`.

## Масштабирование
core-api, web, media-worker, notifier — stateless, масштабируются добавлением экземпляров. Ограничения: Telegram-опрос (`TELEGRAM_POLLING_ENABLED`) включать ровно в одном экземпляре notifier; параллелизм media-worker ≤ числу партиций топика (6).

## Резервное копирование (NFR-REL-02, цель RPO ≤ 15 мин, RTO ≤ 4 ч)
- PostgreSQL: непрерывная архивация WAL (pgBackRest/wal-g) + ежедневный базовый бэкап.
- MongoDB: ежедневный `mongodump --oplog` + снапшоты тома; oplog-окно ≥ 24 ч.
- S3: версионирование бакета + репликация в другой регион.
- Проверка восстановления — ежемесячно на отдельном стенде: восстановить все три хранилища на одну метку времени, поднять compose, прогнать e2e.

## Обновление
Миграции Flyway применяются автоматически при старте core-api и совместимы назад (expand/contract, NFR-REL-03): сначала обновляются экземпляры core-api по одному, затем web и воркеры.

## Мониторинг
`/health/live`, `/health/ready` (core-api, воркеры), метрики Prometheus `/actuator/prometheus`, JSON-логи с `traceId`. Kafka DLQ-топики `*.dlq` — алерт на ненулевой размер.
