// Command media-worker transcodes uploaded videos to HLS (architecture.md §5.2).
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/tutorcraft/media-worker/internal/config"
	"github.com/tutorcraft/media-worker/internal/ffmpeg"
	"github.com/tutorcraft/media-worker/internal/processor"
	"github.com/tutorcraft/media-worker/internal/s3"
	"github.com/tutorcraft/media-worker/internal/storage"
	"github.com/tutorcraft/workerkit/envconfig"
	"github.com/tutorcraft/workerkit/health"
	"github.com/tutorcraft/workerkit/kafkax"
	"github.com/tutorcraft/workerkit/lifecycle"
	"github.com/tutorcraft/workerkit/logging"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/ttlcache"
)

const (
	serviceName           = "media-worker"
	processedCacheSize    = 10_000
	processedCacheTTL     = 24 * time.Hour
	httpReadHeaderTimeout = 5 * time.Second
	httpShutdownTimeout   = 5 * time.Second
	dlqAttempts           = 5
)

func main() {
	if err := run(); err != nil {
		fatal := slog.New(slog.NewJSONHandler(os.Stderr, nil)).With(slog.String("service", serviceName))
		fatal.Error("media-worker stopped with error", slog.String("error", err.Error()))
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load(envconfig.FromOS())
	if err != nil {
		return err
	}
	log, err := logging.New(os.Stdout, serviceName, cfg.LogLevel)
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, os.Interrupt)
	defer stop()

	writer := kafkax.NewWriter(cfg.KafkaBrokers)
	defer closeAndLog(log, "kafka writer", writer.Close)
	producer := kafkax.NewProducer(writer)
	proc := processor.New(newStore(cfg), newTool(cfg), producer, processorSettings(cfg), log)

	components := consumers(cfg, log, proc, producer)
	components = append(components, healthServer(cfg))
	log.Info("media-worker started", slog.Int("concurrency", cfg.WorkerConcurrency), slog.String("topic", cfg.UploadedTopic))
	err = lifecycle.Run(ctx, components...)
	log.Info("media-worker stopped")
	return err
}

func newStore(cfg config.Config) storage.Store {
	return storage.Store{Client: s3.NewClient(s3.Config{
		Endpoint:    cfg.S3Endpoint,
		Region:      cfg.S3Region,
		Credentials: s3.Credentials{AccessKeyID: cfg.S3AccessKey, SecretAccessKey: cfg.S3SecretKey},
	})}
}

func newTool(cfg config.Config) ffmpeg.Runner {
	return ffmpeg.Runner{FFmpegPath: cfg.FFmpegPath, FFprobePath: cfg.FFprobePath, TranscodeTimeout: cfg.TranscodeTimeout}
}

func processorSettings(cfg config.Config) processor.Settings {
	return processor.Settings{
		Bucket:            cfg.S3Bucket,
		MaxVideoBytes:     cfg.MaxVideoBytes,
		WorkDir:           cfg.WorkDir,
		ProcessedTopic:    cfg.ProcessedTopic,
		UploadParallelism: cfg.UploadParallelism,
	}
}

// consumers creates WORKER_CONCURRENCY group members; each processes its
// partitions sequentially, so offsets are committed in order.
func consumers(cfg config.Config, log *slog.Logger, handler kafkax.Handler, producer *kafkax.Producer) []lifecycle.Component {
	processed := ttlcache.New[struct{}](processedCacheSize, processedCacheTTL)
	deadLetters := kafkax.NewDeadLetters(producer)
	dlqPolicy := retry.DefaultPolicy()
	dlqPolicy.Attempts = dlqAttempts
	components := make([]lifecycle.Component, 0, cfg.WorkerConcurrency)
	for worker := range cfg.WorkerConcurrency {
		components = append(components, func(ctx context.Context) error {
			reader := kafkax.NewReader(cfg.KafkaBrokers, cfg.KafkaGroupID, cfg.UploadedTopic)
			defer closeAndLog(log, "kafka reader", reader.Close)
			consumer := &kafkax.Consumer{
				Reader: reader, Handler: handler, DeadLetters: deadLetters, Processed: processed,
				Retry: retry.DefaultPolicy(), DLQRetry: dlqPolicy, Log: log.With(slog.Int("worker", worker)),
			}
			work, cancel := lifecycle.GraceContext(ctx, cfg.ShutdownTimeout)
			defer cancel()
			return consumer.Run(ctx, work)
		})
	}
	return components
}

func healthServer(cfg config.Config) lifecycle.Component {
	mux := http.NewServeMux()
	probe := health.Register(mux, health.TCPCheck("kafka", cfg.KafkaBrokers))
	srv := &http.Server{Addr: cfg.HTTPAddr, Handler: mux, ReadHeaderTimeout: httpReadHeaderTimeout}
	return lifecycle.HTTPServer(srv, httpShutdownTimeout, probe.MarkShuttingDown)
}

func closeAndLog(log *slog.Logger, name string, closeFn func() error) {
	if err := closeFn(); err != nil {
		log.Warn("close failed", slog.String("component", name), slog.String("error", err.Error()))
	}
}
