// Package config loads notifier settings from the environment and fails fast
// on any invalid value. Secrets are never included in error messages.
package config

import (
	"net/mail"
	"net/url"
	"strings"
	"time"

	"github.com/tutorcraft/notifier/internal/auth"
	"github.com/tutorcraft/notifier/internal/telegram"
	"github.com/tutorcraft/workerkit/envconfig"
)

const (
	defaultHTTPAddr        = ":8090"
	defaultLogLevel        = "info"
	defaultGroupID         = "notifier"
	defaultTopicPrefix     = "tc"
	defaultConcurrency     = 4
	maxConcurrency         = 32
	defaultSMTPPort        = 1025
	smtpsPort              = 465 // SMTPS: TLS from the first byte instead of STARTTLS
	maxPort                = 65535
	defaultSMTPTimeout     = 15 * time.Second
	defaultShutdownTimeout = 15 * time.Second
	minTimeout             = time.Second
	maxTimeout             = 5 * time.Minute
	defaultPollTimeoutSec  = 30
	maxPollTimeoutSec      = 50
	boolTrue               = "true"

	requestedTopicSuffix = ".notify.requested.v1"
	deliveredTopicSuffix = ".notify.delivered.v1"
	linkedTopicSuffix    = ".telegram.linked.v1"
)

// SMTP settings; Host == "" disables the email channel.
type SMTP struct {
	Host     string
	Port     int
	Username string
	Password string
	From     *mail.Address
	Timeout  time.Duration
	// ImplicitTLS: connect over TLS right away (port 465 — Gmail, Yandex, Mail.ru); otherwise STARTTLS if offered.
	ImplicitTLS bool
}

// Telegram settings; BotToken == "" disables the telegram channel and the linking bot.
type Telegram struct {
	BotToken       string
	APIBaseURL     string
	PollingEnabled bool
	PollTimeoutSec int
}

// Config is the complete notifier configuration.
type Config struct {
	HTTPAddr        string
	LogLevel        string
	KafkaBrokers    []string
	KafkaGroupID    string
	RequestedTopic  string
	DeliveredTopic  string
	LinkedTopic     string
	Concurrency     int
	JWTSecret       string
	WebOrigin       *url.URL
	PublicBaseURL   *url.URL
	SMTP            SMTP
	Telegram        Telegram
	ShutdownTimeout time.Duration
}

// Load reads and validates the configuration.
func Load(env *envconfig.Loader) (Config, error) {
	prefix := env.String("KAFKA_TOPIC_PREFIX", defaultTopicPrefix)
	cfg := Config{
		HTTPAddr:        env.String("HTTP_ADDR", defaultHTTPAddr),
		LogLevel:        env.String("LOG_LEVEL", defaultLogLevel),
		KafkaBrokers:    env.RequiredList("KAFKA_BROKERS"),
		KafkaGroupID:    env.String("KAFKA_GROUP_ID", defaultGroupID),
		RequestedTopic:  prefix + requestedTopicSuffix,
		DeliveredTopic:  prefix + deliveredTopicSuffix,
		LinkedTopic:     prefix + linkedTopicSuffix,
		Concurrency:     env.Int("NOTIFIER_CONCURRENCY", defaultConcurrency, 1, maxConcurrency),
		JWTSecret:       loadJWTSecret(env),
		WebOrigin:       env.HTTPURL("WEB_ORIGIN", ""),
		PublicBaseURL:   env.HTTPURL("PUBLIC_BASE_URL", ""),
		SMTP:            loadSMTP(env),
		Telegram:        loadTelegram(env),
		ShutdownTimeout: env.Duration("SHUTDOWN_TIMEOUT", defaultShutdownTimeout, minTimeout, maxTimeout),
	}
	return cfg, env.Err()
}

func loadJWTSecret(env *envconfig.Loader) string {
	secret := env.RequiredString("JWT_SECRET")
	if secret != "" && len(secret) < auth.MinSecretBytes {
		env.Fail("JWT_SECRET", "must be at least 32 bytes")
	}
	return secret
}

func loadSMTP(env *envconfig.Loader) SMTP {
	smtp := SMTP{
		Host:     env.String("SMTP_HOST", ""),
		Port:     env.Int("SMTP_PORT", defaultSMTPPort, 1, maxPort),
		Username: env.String("SMTP_USERNAME", ""),
		Password: env.String("SMTP_PASSWORD", ""),
		Timeout:  env.Duration("SMTP_TIMEOUT", defaultSMTPTimeout, minTimeout, maxTimeout),
	}
	if smtp.Host == "" {
		return smtp
	}
	smtp.ImplicitTLS = smtp.Port == smtpsPort
	from, err := mail.ParseAddress(env.RequiredString("SMTP_FROM"))
	if err != nil {
		env.Fail("SMTP_FROM", "must be an email address, e.g. TutorCraft <no-reply@example.com>")
	}
	smtp.From = from
	return smtp
}

func loadTelegram(env *envconfig.Loader) Telegram {
	apiBaseURL := telegram.DefaultBaseURL
	if parsed := env.HTTPURL("TELEGRAM_API_BASE_URL", telegram.DefaultBaseURL); parsed != nil {
		apiBaseURL = parsed.String()
	}
	return Telegram{
		BotToken:       env.String("TELEGRAM_BOT_TOKEN", ""),
		APIBaseURL:     apiBaseURL,
		PollingEnabled: strings.EqualFold(env.String("TELEGRAM_POLLING_ENABLED", boolTrue), boolTrue),
		PollTimeoutSec: env.Int("TELEGRAM_POLL_TIMEOUT_SEC", defaultPollTimeoutSec, 1, maxPollTimeoutSec),
	}
}
