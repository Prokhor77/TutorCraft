#!/bin/sh
# Бакет + CORS для прямой загрузки из браузера по pre-signed URL.
# Бакет закрыт; анонимное чтение — только HLS-префикс hls/ (ADR-008).
set -eu
mc alias set local http://minio:9000 "$S3_ACCESS_KEY" "$S3_SECRET_KEY"
mc mb --ignore-existing "local/$S3_BUCKET"
mc anonymous set none "local/$S3_BUCKET"
mc anonymous set download "local/$S3_BUCKET/hls"
echo "bucket $S3_BUCKET ready"
