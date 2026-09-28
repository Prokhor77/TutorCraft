package config

import (
	"strings"
	"testing"

	"github.com/tutorcraft/workerkit/envconfig"
)

const testSecret = "a-very-long-jwt-secret-for-tests-0123456789"

func baseEnv() map[string]string {
	return map[string]string{
		"KAFKA_BROKERS":   "kafka:9092",
		"JWT_SECRET":      testSecret,
		"WEB_ORIGIN":      "http://localhost:3000",
		"PUBLIC_BASE_URL": "http://localhost:3000",
		"SMTP_HOST":       "mailpit",
		"SMTP_FROM":       "TutorCraft <no-reply@tutorcraft.local>",
	}
}

func TestLoadDefaults(t *testing.T) {
	cfg, err := Load(envconfig.FromMap(baseEnv()))
	if err != nil {
		t.Fatal(err)
	}
	if cfg.HTTPAddr != ":8090" || cfg.RequestedTopic != "tc.notify.requested.v1" ||
		cfg.DeliveredTopic != "tc.notify.delivered.v1" || cfg.LinkedTopic != "tc.telegram.linked.v1" ||
		cfg.SMTP.Port != 1025 || cfg.SMTP.From.Address != "no-reply@tutorcraft.local" ||
		cfg.Telegram.BotToken != "" || !cfg.Telegram.PollingEnabled || cfg.Telegram.APIBaseURL != "https://api.telegram.org" {
		t.Fatalf("unexpected config %+v", cfg)
	}
}

func TestLoadSMTPOptional(t *testing.T) {
	env := baseEnv()
	delete(env, "SMTP_HOST")
	delete(env, "SMTP_FROM")
	cfg, err := Load(envconfig.FromMap(env))
	if err != nil || cfg.SMTP.Host != "" {
		t.Fatalf("email should be optional: %v", err)
	}
}

func TestLoadFailsFastWithoutLeakingSecrets(t *testing.T) {
	env := baseEnv()
	env["JWT_SECRET"] = "short-secret-value"
	env["WEB_ORIGIN"] = "localhost"
	env["SMTP_FROM"] = "not an address"
	delete(env, "KAFKA_BROKERS")
	_, err := Load(envconfig.FromMap(env))
	if err == nil {
		t.Fatal("expected error")
	}
	for _, key := range []string{"JWT_SECRET", "WEB_ORIGIN", "SMTP_FROM", "KAFKA_BROKERS"} {
		if !strings.Contains(err.Error(), key) {
			t.Errorf("error does not mention %s: %v", key, err)
		}
	}
	if strings.Contains(err.Error(), "short-secret-value") {
		t.Fatal("secret leaked into error")
	}
}
