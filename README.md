# TutorCraft

Платформа онлайн-обучения: удобная альтернатива Moodle и SaaS для репетиторов (создание и продажа курсов, тесты, проверка работ, Telegram-уведомления).

- Требования: [`docs/SPEC.md`](docs/SPEC.md) · Архитектура: [`docs/architecture.md`](docs/architecture.md) · Статус: [`docs/ROADMAP.md`](docs/ROADMAP.md)
- API: [`docs/api/contract.md`](docs/api/contract.md) (OpenAPI генерируется из кода: `GET /api/v1/openapi.json`, UI: `/api/docs`)
- События Kafka: [`docs/events/README.md`](docs/events/README.md) · Права: [`docs/permissions.md`](docs/permissions.md) · Решения: [`docs/decisions`](docs/decisions)
- Дизайн: [`design/stitch`](design/stitch) (экспорт Google Stitch) и [`design/stitch-design-system.md`](design/stitch-design-system.md)

## Запуск одной командой

Нужны Docker Desktop (или Docker Engine + Compose v2), `openssl`, `python3`.

```bash
./scripts/init-env.sh          # создаёт .env со случайными локальными секретами (не коммитится)
docker compose up --build      # первый запуск ~5–10 минут: сборка Java, Go, Next.js
```

| Сервис | Адрес |
|---|---|
| Веб-приложение | http://localhost:3000 |
| Core API + Swagger UI | http://localhost:8080/api/docs |
| Почта (Mailpit) | http://localhost:8025 |
| MinIO (консоль) | http://localhost:9001 |

### Демо-данные
При `SEED_DEMO_DATA=true` (по умолчанию в dev) создаются: школа `demo`, преподаватель `teacher@demo.local`
(администратор школы), 20 студентов `student1@demo.local` … `student20@demo.local`, демо-курс с модулями, страницей,
заданием, тестом из 5 вопросов и форумом. Пароль всех демо-аккаунтов — значение `SEED_DEMO_PASSWORD` из вашего `.env`.
Витрина школы: http://localhost:3000/c/demo

### Оплата, Google и Telegram
- По умолчанию `PAYMENT_PROVIDER=fake`: кнопка «Купить» ведёт на тестовую страницу оплаты. Для реальных платежей задайте `yookassa` или `stripe` и ключи в `.env`.
- Вход через Google: `GOOGLE_CLIENT_ID`. Telegram: `TELEGRAM_BOT_TOKEN` и `TELEGRAM_BOT_USERNAME` (бот создаётся у @BotFather; кнопка в ссылке уведомлений требует публичный `PUBLIC_BASE_URL`).

## Структура
```
apps/web              Next.js (App Router, TS, Tailwind, React Query, Zustand, next-intl)
services/core-api     Java 21 + Spring Boot 3 — модульный монолит (PostgreSQL, MongoDB, Redis, S3, Kafka)
services/media-worker Go — транскодирование видео в HLS (FFmpeg)
services/notifier     Go — email, Telegram-бот, WebSocket
services/workerkit    Go — общий каркас воркеров (Kafka-консьюмер, DLQ, health)
infra/                инициализация Kafka-топиков и MinIO
loadtests/            k6-сценарии (AC-10)
```

## Разработка и тесты
```bash
cd services/core-api && mvn verify                 # unit + ArchUnit; IT (Testcontainers): mvn failsafe:integration-test
cd services/media-worker && go test ./...          # аналогично notifier, workerkit
cd apps/web && npm ci && npm test && npm run build # e2e: npm run test:e2e (при запущенном compose)
```
Правила кода — [`docs/dev/backend-conventions.md`](docs/dev/backend-conventions.md), фронтенд — [`apps/web/README.md`](apps/web/README.md).

## Безопасность
Секреты только в `.env`/хранилище секретов, в репозитории — `.env.example` без значений. Токены хранятся хешами, секреты интеграций шифруются (AES-GCM, `DATA_ENCRYPTION_KEY`).
