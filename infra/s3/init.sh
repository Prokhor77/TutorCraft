#!/bin/sh
# Создание бакета (идемпотентно).
set -eu
: "${S3_BUCKET:?S3_BUCKET is required}"
MASTER="s3:9333"
ATTEMPTS=30
i=0
until echo "s3.bucket.list" | weed shell -master="$MASTER" >/tmp/buckets 2>/dev/null; do
  i=$((i + 1))
  [ "$i" -ge "$ATTEMPTS" ] && { echo "S3 master is not ready" >&2; exit 1; }
  sleep 2
done
if grep -q "^  *${S3_BUCKET}\b\|^${S3_BUCKET}\b" /tmp/buckets; then
  echo "bucket ${S3_BUCKET} already exists"
  exit 0
fi
echo "s3.bucket.create -name ${S3_BUCKET}" | weed shell -master="$MASTER"
echo "bucket ${S3_BUCKET} ready"
