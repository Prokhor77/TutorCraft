# Changelog

## Unreleased
- Письма приглашений и сброса пароля оформлены: фирменная карточка, кнопка действия («Задать пароль и начать»),
  запасная ссылка под кнопкой, превью в списке писем, служебный футер для категории `account`. Приглашение в курс
  называет курс, школу и того, кто пригласил, и срок действия ссылки (`identity.course_invitation`). В
  `tc.notify.requested.v1` — необязательное поле `actionLabel` (i18n-ключ `<code>.action`). notifier поддерживает
  SMTPS: порт 465 — TLS сразу (Gmail, Яндекс, Mail.ru). Настройка почты на сервере по IP — `docs/deployment-ip.md` §6.2.
- Модель монетизации: платит только репетитор (школа) — подписка 1 месяц / 3 месяца / 1 год за 30 / 75 / 150 USD
  (ADR-012); курсы для учеников бесплатны, число курсов и учеников не ограничено. Удалена продажа курсов: цена курса
  (`PUT /courses/{id}/price`, поле `price`), заказы и `/checkout`, вебхуки YooKassa/Stripe, «Администрирование → Продажи»,
  уведомление `sale`, вебхук `order.paid`, ошибка `enrollment.payment_required`. Схема БД не менялась (expand/contract):
  таблицы `orders`, `payment_events` и колонки `courses.price_*` удалит миграция следующего релиза.
- Репетиторы сами приглашают и блокируют учеников, без администратора платформы. Приглашение в курс по email
  (`POST /courses/{id}/invitations`) создаёт аккаунт в школе и отдаёт одноразовую ссылку активации (письмо — если есть SMTP).
  «Записать пользователей» теперь работает у преподавателей (`GET /courses/{id}/enrollment-candidates`). В курсе можно
  приостановить и вернуть доступ; владелец школы получил страницу «Ученики» (`/school/members`, права `member.view/manage`)
  с блокировкой в школе. Администрирование → «Все пользователи» (`/platform/users`): школа, кто и как создал аккаунт,
  блокировка платформой (репетитор её не снимает) и полное удаление (FR-USER-05: SPI `identity.spi.UserDataEraser`
  в каждом модуле, аккаунт обезличивается). Миграция V18.
- Журнал активности (Администрирование → Активность): каждый запрос к API, переходы и ошибки в браузере — кто, что, на
  какой странице, статус и длительность; сводка за 24 ч, фильтры, трассировка ошибки (действия пользователя до и после,
  стек вызовов). Модуль `activity`, миграция V16, `X-Request-Id` в ответах и `requestId` в Problem, «Код ошибки» в
  сообщениях об ошибках сервера. Настройки `ACTIVITY_LOG_*`.

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
