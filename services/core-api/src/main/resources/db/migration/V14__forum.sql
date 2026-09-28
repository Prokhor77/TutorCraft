-- communication.forum: темы, посты (дерево глубиной ≤ 3), подписки, отметки прочтения (FR-FORUM-01..04).

CREATE TABLE forum_discussions (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    course_id    UUID        NOT NULL,
    item_id      UUID        NOT NULL,
    author_id    UUID        NOT NULL REFERENCES users (id),
    title        TEXT        NOT NULL,
    pinned       BOOLEAN     NOT NULL DEFAULT false,
    locked       BOOLEAN     NOT NULL DEFAULT false,
    reply_count  INT         NOT NULL DEFAULT 0 CHECK (reply_count >= 0),
    last_post_at TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX forum_discussions_item_idx ON forum_discussions (tenant_id, item_id, last_post_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE forum_posts (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    course_id     UUID        NOT NULL,
    item_id       UUID        NOT NULL,
    discussion_id UUID        NOT NULL REFERENCES forum_discussions (id),
    parent_id     UUID REFERENCES forum_posts (id),
    depth         SMALLINT    NOT NULL CHECK (depth BETWEEN 0 AND 3),
    author_id     UUID        NOT NULL REFERENCES users (id),
    body          JSONB       NOT NULL,
    hidden        BOOLEAN     NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL,
    edited_at     TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX forum_posts_discussion_idx ON forum_posts (tenant_id, discussion_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX forum_posts_parent_idx ON forum_posts (parent_id) WHERE deleted_at IS NULL;
CREATE INDEX forum_posts_course_recent_idx ON forum_posts (tenant_id, course_id, created_at DESC)
    WHERE deleted_at IS NULL AND NOT hidden;

CREATE TABLE forum_subscriptions (
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    discussion_id UUID        NOT NULL REFERENCES forum_discussions (id),
    user_id       UUID        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, discussion_id, user_id)
);

CREATE TABLE forum_reads (
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    discussion_id UUID        NOT NULL REFERENCES forum_discussions (id),
    user_id       UUID        NOT NULL REFERENCES users (id),
    last_read_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, discussion_id, user_id)
);
