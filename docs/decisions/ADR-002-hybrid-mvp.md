# ADR-002: Гибридный MVP (LMS + SaaS для репетиторов)
Статус: принято · 2026-09-27

**Контекст.** ТЗ описывает LMS для организаций (email+пароль, оплата — P2). Команда видит продукт как SaaS для репетиторов (вход через Google/Telegram, продажа курсов, Telegram-пуши). Заказчик выбрал «гибрид».

**Решение.** В P0 дополнительно к ТЗ:
- `FR-AUTH-HYB-01` Вход через Google (OIDC ID-token) и Telegram Login Widget.
- `FR-ENROL-09` (платная запись) поднят из P2 в P0: цена курса, заказ, PaymentGateway (YooKassa, Stripe, Fake для dev), запись после оплаты.
- `FR-NOTIF-HYB-01` Канал уведомлений Telegram (привязка чата через deep-link бота).
- `FR-COURSE-HYB-01` Публичный SSR-лендинг курса `/c/{tenantSlug}/{courseSlug}` (SEO).
- Каждый репетитор = tenant (режим «личная школа»); организации — тоже tenant. Код один (ТЗ 2.2).

**Последствия.** Биллинг — отдельный модуль `billing` с PostgreSQL; деньги в минимальных единицах (`amount_minor BIGINT`) + ISO-4217 валюта.
