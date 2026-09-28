# syntax=docker/dockerfile:1
# Build context: ./services (the service depends on ../workerkit), see docker-compose.yml.

FROM golang:1.24-alpine AS build
WORKDIR /src
COPY workerkit/go.mod workerkit/go.sum ./workerkit/
COPY media-worker/go.mod media-worker/go.sum ./media-worker/
WORKDIR /src/media-worker
RUN go mod download
COPY workerkit/ /src/workerkit/
COPY media-worker/ /src/media-worker/
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/media-worker ./cmd/media-worker

FROM alpine:3.20
RUN apk add --no-cache ffmpeg ca-certificates \
    && addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app
# Transcoding scratch space, writable by the non-root user.
RUN mkdir -p /work && chown 10001:10001 /work
ENV WORK_DIR=/work
COPY --from=build /out/media-worker /usr/local/bin/media-worker
USER 10001:10001
EXPOSE 8091
ENTRYPOINT ["/usr/local/bin/media-worker"]
