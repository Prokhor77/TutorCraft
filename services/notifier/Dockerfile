# syntax=docker/dockerfile:1
# Build context: ./services (the service depends on ../workerkit), see docker-compose.yml.

FROM golang:1.27-alpine AS build
WORKDIR /src
COPY workerkit/go.mod workerkit/go.sum ./workerkit/
COPY notifier/go.mod notifier/go.sum ./notifier/
WORKDIR /src/notifier
RUN go mod download
COPY workerkit/ /src/workerkit/
COPY notifier/ /src/notifier/
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/notifier ./cmd/notifier

FROM alpine:3.20
RUN apk add --no-cache ca-certificates \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app
COPY --from=build /out/notifier /usr/local/bin/notifier
USER 10001:10001
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/notifier"]
