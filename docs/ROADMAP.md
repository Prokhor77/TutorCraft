# Дорожная карта и статус реализации

Статус на 2026-09-28. Легенда: ✅ реализовано в коде · 🟡 частично · ⏳ не начато. «Проверено» — чем подтверждено.

## Что проверено и что нет
| Компонент | Сборка | Тесты |
|---|---|---|
| apps/web (Next.js) | ✅ `next build`, tsc, eslint | ✅ 86 unit/a11y (Vitest); e2e (Playwright) написан, не запускался |
| services/media-worker, notifier, workerkit (Go) | ✅ `go vet`, `go build` | ✅ `go test -race` (ffmpeg — реальный) |
| services/core-api (Java) | ✅ `mvn -B verify -DskipITs` на Maven 3.9 / JDK 21 | ✅ 463 unit + ArchUnit проходят; IT (Testcontainers) написаны, не запускались; миграции V1–V15 и все SQL-запросы проверены на PostgreSQL 16 |

Сборка против реальных библиотек проверена 2026-09-28 — прежнее предупреждение о заглушках снято.
На JDK 24+ пять тестов `AuthServiceRefreshTest` падают с `MockitoException`: Byte Buddy не поддерживает
такую версию JVM. Это среда, а не код — на JDK 21 (как в CI) всё зелёное; локально обходится флагом
`-Dnet.bytebuddy.experimental=true`.

**Обновления 2026-09-28** (все — закрытие HIGH/CRITICAL по trivy, NFR-SEC-10):
Spring Boot 3.3.5 → 3.5.16 и springdoc 2.6.0 → 2.8.17 (CRITICAL `CVE-2026-22732`, обход политики
безопасности в spring-security-web, плюс RCE в spring-kafka и spring-data-mongodb); переопределены
netty 4.1.137, tomcat 10.1.60, postgresql 42.7.12, bouncycastle 1.85. Правок в коде не потребовалось,
463 юнит-теста и ArchUnit зелёные. Testcontainers 1.20.3 → 1.21.4: старая версия не договаривается
о версии API с Docker Engine 29.

**Осталось прогнать:** `mvn failsafe:integration-test` (workflow `integration`, нужен Docker) и
`npm run test:e2e` (workflow `e2e`). Оба вынесены из `ci`, чтобы не блокировать автодеплой.

## P0 (MVP) — ТЗ + гибрид
| Область | Статус | Примечание |
|---|---|---|
| AUTH-01..04, OAuth Google/Telegram, регистрация репетитора | ✅ | |
| ACL-01/02, роли/права, аудит | ✅ | ACL-03..05 — P1 |
| USER-01..03 (CRUD, CSV-импорт, приглашения) | ✅ | |
| COURSE-01..07, CONTENT-01..04, блочный редактор | ✅ | |
| ENROL-01..06 | ✅ | |
| ENROL-09 платная запись (Fake/YooKassa/Stripe) | ✅ | возвраты — ⏳ |
| ASSIGN-01..07 | ✅ | |
| QBANK-01/02/03/05/06, QUIZ-01..07 | ✅ | предпросмотр теста преподавателем — ⏳ |
| FORUM-01..04 | ✅ | перенос темы в другой форум — ⏳ |
| NOTIF-01/02/04 + Telegram-канал + WebSocket | ✅ | напоминания игнорируют индивидуальные продления — 🟡 |
| PROG-01..05 | ✅ | |
| GRADE-01..08 | ✅ | |
| DASH-01..03 | ✅ | |
| REPORT-01/02 | ✅ | |
| INTEG-01/02 (API-токены, вебхуки) | 🟡 | OAuth2 client credentials — ⏳ |
| ADMIN-01..03 | 🟡 | ADMIN-03 (экран фоновых задач) — ⏳ |
| CONTENT-06 видео HLS | ✅ | подписанные URL через CDN — P1 (ADR-008) |
| PWA офлайн-чтение | 🟡 | базовый service worker |
| NFR-PERF нагрузочный тест | 🟡 | `loadtests/quiz-autosave.js` (k6) написан, не запускался |

## P1 / P2 — не начато (по ТЗ)
SSO (OIDC/SAML), 2FA, magic link, когорты, анонимизация/экспорт ПДн, шаблоны и потоки курсов, экспорт/импорт курса,
каталог с самозаписью (🟡 публичный каталог есть), антивирус, книга, глоссарий, аннотации PDF, рубрики, слепая проверка,
несколько проверяющих, cloze/drag&drop/вычисляемые вопросы, GIFT/Moodle XML, статистика вопросов, адаптивный режим теста,
реакции, личные сообщения, дайджесты, web push, опросы/анкеты, сертификаты, бейджи, аналитика риска, LTI, SCORM,
ИИ-функции, «Объяснить доступ», «Войти как», кастомные домены, импорт .mbz, H5P, xAPI, видеоконференции.
