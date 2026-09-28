#!/bin/sh
# Запуск SeaweedFS (master + volume + filer + S3-шлюз) с ключами из окружения.
# Доступ без ключа — только чтение бакета (нужно для HLS-плейлистов в локальной разработке, ADR-008/011).
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
      "actions": ["Read:${S3_BUCKET}"]
    }
  ]
}
JSON
exec weed server -dir=/data -ip.bind=0.0.0.0 -s3 -s3.port=8333 -s3.config="$CONFIG" -volume.max=0
