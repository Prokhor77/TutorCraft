# workerkit

Plumbing shared by the Go workers (`media-worker`, `notifier`), referenced through a `replace ../workerkit`
directive, so both services build from the `services/` Docker context. It contains no business logic.

| Package | Purpose |
|---|---|
| `envelope` | Kafka envelope `{eventId, type, version, occurredAt, tenantId, payload}`: decode + validate, build, encode |
| `validate` | field-level validation collector (UUID, required, enum, length); messages never include values |
| `kafkax` | at-least-once consumer loop (idempotency, retries, `<topic>.dlq`), envelope producer, kafka-go factories |
| `retry` | exponential backoff with `Permanent` and `After` (server retry-after hints) |
| `ttlcache` | thread-safe LRU with TTL (processed eventIds, delivery outcomes) |
| `ids` | UUIDv7 generator on `crypto/rand` |
| `envconfig` | typed env reading with accumulated, secret-free errors |
| `health` | `/health/live`, `/health/ready` with dependency checks |
| `lifecycle` | run components, grace context for draining, HTTP server component |
| `logging`, `textsafe` | JSON slog logger; single-line/truncate helpers for error texts |

```bash
cd services/workerkit
export GOPROXY=direct GOSUMDB=off GOFLAGS=-mod=mod
go vet ./... && go test ./...
```
