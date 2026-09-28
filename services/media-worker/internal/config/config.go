// Package config loads media-worker settings from the environment and fails
// fast on any invalid value.
package config

import (
	"net/url"
	"os"
	"time"

	"github.com/tutorcraft/workerkit/envconfig"
)

const (
	defaultHTTPAddr          = ":8091"
	defaultLogLevel          = "info"
	defaultGroupID           = "media-worker"
	defaultTopicPrefix       = "tc"
	defaultRegion            = "us-east-1"
	defaultMaxVideoBytes     = 2 << 30 // 2 GiB
	maxMaxVideoBytes         = 50 << 30
	defaultTranscodeTimeout  = 30 * time.Minute
	minTranscodeTimeout      = 10 * time.Second
	maxTranscodeTimeout      = 6 * time.Hour
	defaultConcurrency       = 2
	maxConcurrency           = 16
	defaultShutdownTimeout   = 30 * time.Second
	minShutdownTimeout       = time.Second
	maxShutdownTimeout       = 6 * time.Hour
	defaultUploadParallelism = 4
	maxUploadParallelism     = 32
	defaultFFmpegPath        = "ffmpeg"
	defaultFFprobePath       = "ffprobe"

	uploadedTopicSuffix  = ".media.video-uploaded.v1"
	processedTopicSuffix = ".media.video-processed.v1"
)

// Config is the complete media-worker configuration.
type Config struct {
	HTTPAddr          string
	LogLevel          string
	KafkaBrokers      []string
	KafkaGroupID      string
	UploadedTopic     string
	ProcessedTopic    string
	S3Endpoint        *url.URL
	S3AccessKey       string
	S3SecretKey       string
	S3Region          string
	S3Bucket          string
	MaxVideoBytes     int64
	TranscodeTimeout  time.Duration
	WorkerConcurrency int
	UploadParallelism int
	WorkDir           string
	FFmpegPath        string
	FFprobePath       string
	ShutdownTimeout   time.Duration
}

// Load reads and validates the configuration.
func Load(env *envconfig.Loader) (Config, error) {
	prefix := env.String("KAFKA_TOPIC_PREFIX", defaultTopicPrefix)
	cfg := Config{
		HTTPAddr:          env.String("HTTP_ADDR", defaultHTTPAddr),
		LogLevel:          env.String("LOG_LEVEL", defaultLogLevel),
		KafkaBrokers:      env.RequiredList("KAFKA_BROKERS"),
		KafkaGroupID:      env.String("KAFKA_GROUP_ID", defaultGroupID),
		UploadedTopic:     prefix + uploadedTopicSuffix,
		ProcessedTopic:    prefix + processedTopicSuffix,
		S3Endpoint:        env.HTTPURL("S3_ENDPOINT", ""),
		S3AccessKey:       env.RequiredString("S3_ACCESS_KEY"),
		S3SecretKey:       env.RequiredString("S3_SECRET_KEY"),
		S3Region:          env.String("S3_REGION", defaultRegion),
		S3Bucket:          env.RequiredString("S3_BUCKET"),
		MaxVideoBytes:     env.Int64("MAX_VIDEO_BYTES", defaultMaxVideoBytes, 1, maxMaxVideoBytes),
		TranscodeTimeout:  env.Duration("TRANSCODE_TIMEOUT", defaultTranscodeTimeout, minTranscodeTimeout, maxTranscodeTimeout),
		WorkerConcurrency: env.Int("WORKER_CONCURRENCY", defaultConcurrency, 1, maxConcurrency),
		UploadParallelism: env.Int("UPLOAD_PARALLELISM", defaultUploadParallelism, 1, maxUploadParallelism),
		WorkDir:           env.String("WORK_DIR", os.TempDir()),
		FFmpegPath:        env.String("FFMPEG_PATH", defaultFFmpegPath),
		FFprobePath:       env.String("FFPROBE_PATH", defaultFFprobePath),
		ShutdownTimeout:   env.Duration("SHUTDOWN_TIMEOUT", defaultShutdownTimeout, minShutdownTimeout, maxShutdownTimeout),
	}
	validateWorkDir(env, cfg.WorkDir)
	return cfg, env.Err()
}

func validateWorkDir(env *envconfig.Loader, dir string) {
	info, err := os.Stat(dir)
	if err != nil || !info.IsDir() {
		env.Fail("WORK_DIR", "must be an existing directory")
	}
}
