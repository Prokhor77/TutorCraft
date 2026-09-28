# ADR-011: SeaweedFS вместо MinIO для локального S3
Статус: принято · 2026-09-28

**Контекст.** Образы `minio/minio` и `quay.io/minio/minio` перестали быть публично доступными, `docker compose up` падал на загрузке.

**Решение.** Локально используется SeaweedFS (`chrislusf/seaweedfs`) в режиме `weed server -s3`: S3 API (SigV4, pre-signed URL, path-style), CORS по умолчанию разрешён. Ключи — из `S3_ACCESS_KEY/S3_SECRET_KEY`, бакет создаёт `s3-init`. Код не меняется: core-api (AWS SDK) и media-worker (SigV4) работают с любым S3-совместимым хранилищем.

**Ограничение (только dev).** Анонимное чтение разрешено на весь бакет, а не только на префикс `hls/` (SeaweedFS не поддерживает префиксные правила так же, как MinIO). В продакшне — AWS S3 / Yandex Object Storage с приватным бакетом и выдачей HLS через CDN (ADR-008).
