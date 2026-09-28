-- files: метаданные файлов (NFR-SEC-05), связи с владельцами (FileLink), статусы видео (architecture.md §5.2).

CREATE TABLE files (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL REFERENCES tenants (id),
    uploaded_by    UUID        NOT NULL REFERENCES users (id),
    name           TEXT        NOT NULL,
    size_bytes     BIGINT      NOT NULL CHECK (size_bytes > 0),
    declared_mime  TEXT        NOT NULL,
    mime           TEXT        NOT NULL,
    purpose        TEXT        NOT NULL CHECK (purpose IN ('content', 'submission', 'avatar', 'cover', 'video', 'import')),
    status         TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'ready', 'rejected')),
    storage_key    TEXT        NOT NULL,
    sha256         TEXT,
    scan_status    TEXT        NOT NULL DEFAULT 'not_scanned' CHECK (scan_status IN ('not_scanned', 'clean', 'infected')),
    reject_reason  TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    completed_at   TIMESTAMPTZ
);
CREATE INDEX files_tenant_created_idx ON files (tenant_id, created_at DESC);
CREATE INDEX files_tenant_sha_idx ON files (tenant_id, sha256) WHERE status = 'ready' AND sha256 IS NOT NULL;
CREATE INDEX files_tenant_usage_idx ON files (tenant_id) WHERE status <> 'rejected';

CREATE TABLE file_links (
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    file_id     UUID        NOT NULL REFERENCES files (id),
    owner_type  TEXT        NOT NULL,
    owner_id    UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (file_id, owner_type, owner_id)
);
CREATE INDEX file_links_owner_idx ON file_links (tenant_id, owner_type, owner_id);

CREATE TABLE videos (
    file_id              UUID PRIMARY KEY REFERENCES files (id),
    tenant_id            UUID        NOT NULL REFERENCES tenants (id),
    status               TEXT        NOT NULL CHECK (status IN ('processing', 'ready', 'failed')),
    hls_prefix           TEXT,
    master_playlist_key  TEXT,
    duration_sec         INT,
    renditions           JSONB,
    error                TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX videos_tenant_idx ON videos (tenant_id);
