#!/bin/sh
# SeaweedFS для продакшна: master + volume + filer + S3-шлюз.
#
# Отличие от infra/s3/start.sh (dev): анонимный доступ ограничен префиксом hls/, а не всем
# бакетом. Плейлисты и чанки HLS раздаются без подписи (ADR-008), всё остальное — оригиналы
# видео, вложения к сдачам, файлы курсов — только по pre-signed URL от core-api.
#
# Если после деплоя видео не играет с 403 на .m3u8 — эта строка (prefix в действии Read)
# единственное, что стоит проверить: часть сборок SeaweedFS понимает только "Read:<bucket>".
set -eu
: "${S3_ACCESS_KEY:?S3_ACCESS_KEY is required}"
: "${S3_SECRET_KEY:?S3_SECRET_KEY is required}"
: "${S3_BUCKET:?S3_BUCKET is required}"
CONFIG=/tmp/s3.json
cat > "$CONFIG" <<JSON
{
  "identities": [
    {
      "name": "app",
      "credentials": [{ "accessKey": "${S3_ACCESS_KEY}", "secretKey": "${S3_SECRET_KEY}" }],
      "actions": ["Admin", "Read", "List", "Tagging", "Write"]
    },
    {
      "name": "anonymous",
      "actions": ["Read:${S3_BUCKET}/hls/"]
    }
  ]
}
JSON
exec weed server -dir=/data -ip.bind=0.0.0.0 -s3 -s3.port=8333 -s3.config="$CONFIG" -volume.max=0
