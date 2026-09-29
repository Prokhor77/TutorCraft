// Command notifier delivers notifications over email, Telegram and WebSocket
// and runs the Telegram linking bot (architecture.md §5.3).
package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/tutorcraft/notifier/internal/auth"
	"github.com/tutorcraft/notifier/internal/config"
	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/email"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/notifier/internal/realtime"
	"github.com/tutorcraft/notifier/internal/telegram"
	"github.com/tutorcraft/workerkit/envconfig"
	"github.com/tutorcraft/workerkit/health"
	"github.com/tutorcraft/workerkit/kafkax"
	"github.com/tutorcraft/workerkit/lifecycle"
	"github.com/tutorcraft/workerkit/logging"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/ttlcache"
)

const (
	serviceName           = "notifier"
	wsPath                = "GET /ws"
	idempotencyCacheSize  = 50_000
	idempotencyCacheTTL   = 24 * time.Hour
	httpReadHeaderTimeout = 5 * time.Second
	channelRetryMaxDelay  = 30 * time.Second // allows Telegram retry_after up to 30s
	dlqAttempts           = 5
)

func main() {
	if err := run(); err != nil {
		fatal := slog.New(slog.NewJSONHandler(os.Stderr, nil)).With(slog.String("service", serviceName))
		fatal.Error("notifier stopped with error", slog.String("error", err.Error()))
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
	hub := realtime.NewHub(realtime.MaxConnsPerUser, log)
	telegramClient := newTelegramClient(cfg)
	dispatcher := newDispatcher(cfg, log, producer, hub, telegramClient)

	components := consumers(cfg, log, dispatcher, producer)
	components = append(components, httpServer(cfg, log, hub))
	if telegramClient != nil && cfg.Telegram.PollingEnabled {
		components = append(components, newLinker(cfg, log, telegramClient, producer).Run)
	}
	log.Info("notifier started", slog.Int("concurrency", cfg.Concurrency),
		slog.Bool("email", cfg.SMTP.Host != ""), slog.Bool("telegram", telegramClient != nil))
	err = lifecycle.Run(ctx, components...)
	log.Info("notifier stopped")
	return err
}

// newTelegramClient returns nil when no bot token is configured.
func newTelegramClient(cfg config.Config) *telegram.Client {
	if cfg.Telegram.BotToken == "" {
		return nil
	}
	return telegram.NewClient(cfg.Telegram.APIBaseURL, cfg.Telegram.BotToken)
}

func newDispatcher(cfg config.Config, log *slog.Logger, producer *kafkax.Producer, hub *realtime.Hub,
	telegramClient *telegram.Client) *delivery.Dispatcher {
	links := notification.NewLinkResolver(cfg.PublicBaseURL)
	var mailer email.Transport
	if cfg.SMTP.Host != "" {
		mailer = email.SMTPTransport{Host: cfg.SMTP.Host, Port: cfg.SMTP.Port, Username: cfg.SMTP.Username,
			Password: cfg.SMTP.Password, Timeout: cfg.SMTP.Timeout, ImplicitTLS: cfg.SMTP.ImplicitTLS}
	}
	var telegramAPI telegram.MessageAPI
	if telegramClient != nil {
		telegramAPI = telegramClient
	}
	policy := retry.DefaultPolicy()
	policy.MaxDelay = channelRetryMaxDelay
	return delivery.NewDispatcher(delivery.Options{
		Senders: []delivery.Sender{
			email.NewSender(mailer, cfg.SMTP.From, links),
			telegram.NewSender(telegramAPI, links),
			realtime.NewSender(hub),
		},
		Publisher: producer, DeliveredTopic: cfg.DeliveredTopic, Retry: policy, Log: log,
		Outcomes: ttlcache.New[notification.Delivered](idempotencyCacheSize, idempotencyCacheTTL),
	})
}

// consumers creates NOTIFIER_CONCURRENCY consumer-group members.
func consumers(cfg config.Config, log *slog.Logger, handler kafkax.Handler, producer *kafkax.Producer) []lifecycle.Component {
	processed := ttlcache.New[struct{}](idempotencyCacheSize, idempotencyCacheTTL)
	deadLetters := kafkax.NewDeadLetters(producer)
	dlqPolicy := retry.DefaultPolicy()
	dlqPolicy.Attempts = dlqAttempts
	components := make([]lifecycle.Component, 0, cfg.Concurrency)
	for worker := range cfg.Concurrency {
		components = append(components, func(ctx context.Context) error {
			reader := kafkax.NewReader(cfg.KafkaBrokers, cfg.KafkaGroupID, cfg.RequestedTopic)
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

// httpServer serves /ws and the health endpoints on one port. Hijacked
// WebSocket connections are not closed by http.Server.Shutdown, so the hub
// closes them after the listener has stopped.
func httpServer(cfg config.Config, log *slog.Logger, hub *realtime.Hub) lifecycle.Component {
	mux := http.NewServeMux()
	probe := health.Register(mux, health.TCPCheck("kafka", cfg.KafkaBrokers))
	validator := auth.NewValidator(cfg.JWTSecret, time.Now)
	mux.Handle(wsPath, realtime.NewHandler(validator, hub, cfg.WebOrigin.String(), log))
	srv := &http.Server{Addr: cfg.HTTPAddr, Handler: mux, ReadHeaderTimeout: httpReadHeaderTimeout}
	serve := lifecycle.HTTPServer(srv, cfg.ShutdownTimeout, probe.MarkShuttingDown)
	return func(ctx context.Context) error {
		err := serve(ctx)
		hub.CloseAll()
		return err
	}
}

func newLinker(cfg config.Config, log *slog.Logger, api telegram.UpdatesAPI, producer *kafkax.Producer) *telegram.Linker {
	return &telegram.Linker{
		API: api, Publisher: producer, LinkedTopic: cfg.LinkedTopic, PollTimeoutSec: cfg.Telegram.PollTimeoutSec,
		Retry: retry.DefaultPolicy(), Log: log.With(slog.String("component", "telegram-linker")), Now: time.Now,
	}
}

func closeAndLog(log *slog.Logger, name string, closeFn func() error) {
	if err := closeFn(); err != nil {
		log.Warn("close failed", slog.String("component", name), slog.String("error", err.Error()))
	}
}
