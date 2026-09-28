-- courses: метаданные курса — источник истины для прав, каталога и цены (ADR-004, FR-COURSE-02/04/07, FR-COURSE-HYB-01).
-- Структура (модули, элементы) — MongoDB, коллекции modules/items.

CREATE TABLE courses (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID        NOT NULL REFERENCES tenants (id),
    category_id         UUID REFERENCES categories (id),
    title               TEXT        NOT NULL,
    short_name          TEXT,
    slug                TEXT        NOT NULL,
    description         JSONB,
    cover_file_id       UUID,
    starts_at           TIMESTAMPTZ,
    ends_at             TIMESTAMPTZ,
    visibility          TEXT        NOT NULL DEFAULT 'hidden' CHECK (visibility IN ('published', 'hidden', 'scheduled')),
    publish_at          TIMESTAMPTZ,
    self_enrol          JSONB       NOT NULL DEFAULT '{"enabled":false,"code":null,"maxStudents":null,"until":null}',
    price_amount_minor  BIGINT CHECK (price_amount_minor IS NULL OR price_amount_minor >= 0),
    price_currency      TEXT,
    completion_rule     JSONB       NOT NULL DEFAULT '{"requiredItemIds":[],"minFinalPercent":null}',
    group_mode          TEXT        NOT NULL DEFAULT 'none' CHECK (group_mode IN ('none', 'visible', 'separate')),
    created_by          UUID REFERENCES users (id),
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT courses_price_complete CHECK ((price_amount_minor IS NULL) = (price_currency IS NULL)),
    CONSTRAINT courses_dates_order CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT courses_scheduled_has_date CHECK (visibility <> 'scheduled' OR publish_at IS NOT NULL)
);

-- slug и краткое имя уникальны в tenant, включая курсы в корзине (восстановление без конфликтов).
CREATE UNIQUE INDEX courses_tenant_slug_uq ON courses (tenant_id, slug);
CREATE UNIQUE INDEX courses_tenant_short_name_uq ON courses (tenant_id, lower(short_name)) WHERE short_name IS NOT NULL;

-- Списки курсов tenant (keyset по created_at, id) и фильтр по категории.
CREATE INDEX courses_tenant_created_idx ON courses (tenant_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX courses_tenant_category_idx ON courses (tenant_id, category_id) WHERE deleted_at IS NULL;
-- Публичный каталог (опубликованные курсы tenant).
CREATE INDEX courses_public_catalog_idx ON courses (tenant_id, visibility, publish_at) WHERE deleted_at IS NULL;
-- Корзина и очистка по сроку хранения.
CREATE INDEX courses_deleted_idx ON courses (deleted_at) WHERE deleted_at IS NOT NULL;
