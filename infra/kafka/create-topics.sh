#!/usr/bin/env bash
# Идемпотентное создание топиков (docs/events/README.md).
set -euo pipefail
BOOTSTRAP="kafka:9092"
PARTITIONS=6
TOPICS=(
  tc.media.video-uploaded.v1
  tc.media.video-processed.v1
  tc.notify.requested.v1
  tc.notify.delivered.v1
  tc.telegram.linked.v1
  tc.domain.events.v1
)
for topic in "${TOPICS[@]}"; do
  for name in "$topic" "$topic.dlq"; do
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
      --topic "$name" --partitions "$PARTITIONS" --replication-factor 1
  done
done
echo "topics ready"
