# media-worker

Go worker that transcodes uploaded videos to HLS (architecture.md §5.2, ADR-008).

```
tc.media.video-uploaded.v1 ─► download from S3 ─► ffprobe ─► ffmpeg (HLS 360p/720p) ─► upload hls/{fileId}/… ─► tc.media.video-processed.v1
```

## Layout

| Path | Role |
|---|---|
| `cmd/media-worker` | wiring: config, Kafka consumers, health server, graceful shutdown |
| `internal/config` | env → `Config`, validated at start (fail fast, all problems at once) |
| `internal/video` | domain, no I/O: payload validation, rendition ladder, ffprobe parsing, ffmpeg arguments, HLS key layout, failure messages |
| `internal/processor` | use case: orchestrates store → probe → transcode → upload → publish behind ports (`ObjectStore`, `MediaTool`, `EventPublisher`) |
| `internal/ffmpeg` | adapter: runs `ffmpeg`/`ffprobe` via `exec.CommandContext` (no shell), classifies failures |
| `internal/s3` | minimal S3 client: path-style GET/PUT, AWS Signature V4 on `net/http` |
| `internal/storage` | adapter: `s3.Client` → `processor.ObjectStore` (error translation) |
| `../workerkit` | shared with notifier: envelope, validation, Kafka consumer/producer/DLQ, retry, LRU, health, config helpers |

## Behaviour

- **Consumer group** `KAFKA_GROUP_ID` (default `media-worker`). `WORKER_CONCURRENCY` readers join the group; each
  processes its partitions sequentially, so offsets are committed in order and only after the message was handled
  (at-least-once). Effective parallelism is capped by the partition count (6 locally).
- **Validation.** Envelope (`eventId`/`tenantId` UUID, `version == 1`, RFC 3339 `occurredAt`, object payload) and
  payload (`fileId`/`ownerUserId` UUID, `bucket == S3_BUCKET`, safe `objectKey`, `contentType` `video/*`,
  `sizeBytes > 0`). Invalid → `<topic>.dlq` immediately.
- **Pipeline.** Temp workspace `WORK_DIR/job-*` (always removed). Download with a size guard (declared size,
  `Content-Length` and the actual stream are all checked against `MAX_VIDEO_BYTES`). `ffprobe` JSON → duration,
  display height (rotation-aware), audio presence. One `ffmpeg` pass: `split` + `scale=-2:H` per rendition, x264
  `veryfast`, AAC, keyframe forced every 6 s (aligned segments), `-hls_time 6 -hls_playlist_type vod`,
  `master.m3u8`, metadata stripped, input restricted to `-protocol_whitelist file`. Timeout `TRANSCODE_TIMEOUT`.
- **Renditions.** `360p` (800k video + 96k audio) always; `720p` (2800k + 128k) only if source height ≥ 720.
- **Upload.** `hls/{fileId}/{360p|720p}/index.m3u8|seg_NNNNN.ts`, then `hls/{fileId}/master.m3u8` last.
  Content types: `application/vnd.apple.mpegurl`, `video/mp2t`. `UPLOAD_PARALLELISM` concurrent PUTs;
  payload SHA-256 is signed (works with AWS S3 and MinIO).
- **Result.** `status: "ready"` with `hlsPrefix`, `masterPlaylistKey`, `durationSec`, `renditions`; same `tenantId`,
  new UUIDv7 `eventId`. Bad input (too large, missing object, unreadable video, ffmpeg error/timeout) →
  `status: "failed"` with a sanitized `error` (≤ 500 chars, no local paths). See docs/events/README.md.
- **Errors.** Infrastructure errors (S3, Kafka, missing ffmpeg binary) are retried 3× with exponential backoff
  (0.5 s, 1 s), then the original message goes to `tc.media.video-uploaded.v1.dlq` with an `x-error` header. If even
  the DLQ publish fails, the process exits without committing so the message is redelivered after restart.
- **Idempotency.** In-memory LRU of processed `eventId`s (10 000 entries, 24 h TTL) per process. It does not survive
  restarts or span replicas; that is acceptable because reprocessing is safe (same keys are overwritten) and
  core-api's `VideoProcessedListener` is idempotent by `eventId` and file state.
- **Shutdown.** On SIGTERM: readiness turns 503, fetching stops, the in-flight job gets `SHUTDOWN_TIMEOUT` to finish;
  if it is cut off, its offset is not committed (redelivered later).
- **Logs.** JSON (`log/slog`), IDs only: `eventId`, `tenantId`, `fileId`. No object keys, no credentials.

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `KAFKA_BROKERS` | — (required) | comma-separated, e.g. `kafka:9092` |
| `KAFKA_GROUP_ID` | `media-worker` | |
| `KAFKA_TOPIC_PREFIX` | `tc` | topics `<prefix>.media.video-uploaded.v1` / `…video-processed.v1` |
| `S3_ENDPOINT` | — (required) | e.g. `http://s3:8333`; path-style requests |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | — (required) | |
| `S3_BUCKET` | — (required) | the only bucket read and written |
| `S3_REGION` | `us-east-1` | SigV4 region |
| `MAX_VIDEO_BYTES` | `2147483648` (2 GiB) | larger sources → `failed` |
| `TRANSCODE_TIMEOUT` | `30m` | Go duration, 10s…6h |
| `WORKER_CONCURRENCY` | `2` | 1…16 parallel jobs |
| `UPLOAD_PARALLELISM` | `4` | concurrent PUTs per job |
| `WORK_DIR` | OS temp dir (`/work` in the image) | must exist and have room for source + output |
| `FFMPEG_PATH`, `FFPROBE_PATH` | `ffmpeg`, `ffprobe` | |
| `HTTP_ADDR` | `:8091` | `/health/live`, `/health/ready` (ready = Kafka reachable and not shutting down) |
| `SHUTDOWN_TIMEOUT` | `30s` | grace for the in-flight job |
| `LOG_LEVEL` | `info` | `debug`, `info`, `warn`, `error` |

## Build and test

Only GitHub is reachable for Go modules in the dev sandbox, hence the flags (harmless elsewhere):

```bash
cd services/media-worker
export GOPROXY=direct GOSUMDB=off GOFLAGS=-mod=mod
go vet ./...
go test ./...          # add -race for the race detector
go build ./cmd/media-worker
```

`internal/ffmpeg` has integration tests that generate a 2-second clip with `ffmpeg -f lavfi testsrc` and transcode it
for real; they are skipped when `ffmpeg`/`ffprobe` are not on `PATH`. The SigV4 signer is checked against the AWS
documentation examples (GET Object, signing-key derivation).

Docker (context is `services/` because of `workerkit`): `docker compose build media-worker`.
