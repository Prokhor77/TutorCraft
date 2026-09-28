package config

import (
	"strings"
	"testing"
	"time"

	"github.com/tutorcraft/workerkit/envconfig"
)

func baseEnv() map[string]string {
	return map[string]string{
		"KAFKA_BROKERS": "kafka:9092, kafka2:9092",
		"S3_ENDPOINT":   "http://minio:9000",
		"S3_ACCESS_KEY": "ak",
		"S3_SECRET_KEY": "sk",
		"S3_BUCKET":     "tutorcraft",
	}
}

func TestLoadDefaults(t *testing.T) {
	cfg, err := Load(envconfig.FromMap(baseEnv()))
	if err != nil {
		t.Fatal(err)
	}
	if cfg.HTTPAddr != ":8091" || cfg.WorkerConcurrency != 2 || cfg.TranscodeTimeout != 30*time.Minute ||
		cfg.UploadedTopic != "tc.media.video-uploaded.v1" || cfg.ProcessedTopic != "tc.media.video-processed.v1" ||
		len(cfg.KafkaBrokers) != 2 || cfg.S3Endpoint.Host != "minio:9000" {
		t.Fatalf("unexpected config %+v", cfg)
	}
}

func TestLoadFailsFastWithAllProblems(t *testing.T) {
	env := baseEnv()
	delete(env, "S3_SECRET_KEY")
	env["WORKER_CONCURRENCY"] = "0"
	env["TRANSCODE_TIMEOUT"] = "soon"
	env["WORK_DIR"] = "/definitely/missing"
	_, err := Load(envconfig.FromMap(env))
	if err == nil {
		t.Fatal("expected error")
	}
	for _, key := range []string{"S3_SECRET_KEY", "WORKER_CONCURRENCY", "TRANSCODE_TIMEOUT", "WORK_DIR"} {
		if !strings.Contains(err.Error(), key) {
			t.Errorf("error does not mention %s: %v", key, err)
		}
	}
}
