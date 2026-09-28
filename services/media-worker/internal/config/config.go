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

	// DriverLocal stores objects as files under LocalStorageRoot (a volume
	// shared with core-api); DriverS3 talks to an S3-compatible endpoint.
	DriverLocal      = "local"
	DriverS3         = "s3"
	defaultDriver    = DriverLocal
	defaultLocalRoot = "/data/files"
	// LocalBucket is the logical bucket name core-api puts into
	// video-uploaded events when STORAGE_DRIVER=local.
	LocalBucket = "local"
)

// Config is the complete media-worker configuration.
type Config struct {
	HTTPAddr          string
	LogLevel          string
	KafkaBrokers      []string
	KafkaGroupID      string
	UploadedTopic     string
	ProcessedTopic    string
	StorageDriver     string
	LocalStorageRoot  string
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
		StorageDriver:     env.String("STORAGE_DRIVER", defaultDriver),
		MaxVideoBytes:     env.Int64("MAX_VIDEO_BYTES", defaultMaxVideoBytes, 1, maxMaxVideoBytes),
		TranscodeTimeout:  env.Duration("TRANSCODE_TIMEOUT", defaultTranscodeTimeout, minTranscodeTimeout, maxTranscodeTimeout),
		WorkerConcurrency: env.Int("WORKER_CONCURRENCY", defaultConcurrency, 1, maxConcurrency),
		UploadParallelism: env.Int("UPLOAD_PARALLELISM", defaultUploadParallelism, 1, maxUploadParallelism),
		WorkDir:           env.String("WORK_DIR", os.TempDir()),
		FFmpegPath:        env.String("FFMPEG_PATH", defaultFFmpegPath),
		FFprobePath:       env.String("FFPROBE_PATH", defaultFFprobePath),
		ShutdownTimeout:   env.Duration("SHUTDOWN_TIMEOUT", defaultShutdownTimeout, minShutdownTimeout, maxShutdownTimeout),
	}
	loadStorage(env, &cfg)
	validateDir(env, "WORK_DIR", cfg.WorkDir)
	return cfg, env.Err()
}

// loadStorage reads only the settings of the selected driver, so the S3_*
// variables are not required while files live on the server's disk.
func loadStorage(env *envconfig.Loader, cfg *Config) {
	switch cfg.StorageDriver {
	case DriverLocal:
		cfg.LocalStorageRoot = env.String("STORAGE_LOCAL_ROOT", defaultLocalRoot)
		cfg.S3Bucket = LocalBucket
		validateDir(env, "STORAGE_LOCAL_ROOT", cfg.LocalStorageRoot)
	case DriverS3:
		cfg.S3Endpoint = env.HTTPURL("S3_ENDPOINT", "")
		cfg.S3AccessKey = env.RequiredString("S3_ACCESS_KEY")
		cfg.S3SecretKey = env.RequiredString("S3_SECRET_KEY")
		cfg.S3Region = env.String("S3_REGION", defaultRegion)
		cfg.S3Bucket = env.RequiredString("S3_BUCKET")
	default:
		env.Fail("STORAGE_DRIVER", "must be one of: "+DriverLocal+", "+DriverS3)
	}
}

func validateDir(env *envconfig.Loader, key, dir string) {
	info, err := os.Stat(dir)
	if err != nil || !info.IsDir() {
		env.Fail(key, "must be an existing directory")
	}
}
