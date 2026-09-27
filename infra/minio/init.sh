#!/bin/sh
# Бакет + CORS для прямой загрузки из браузера по pre-signed URL.
set -eu
mc alias set local http://minio:9000 "$S3_ACCESS_KEY" "$S3_SECRET_KEY"
mc mb --ignore-existing "local/$S3_BUCKET"
mc anonymous set none "local/$S3_BUCKET"
echo "bucket $S3_BUCKET ready"
