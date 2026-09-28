// Package processor turns a video-uploaded event into HLS renditions in S3
// and a video-processed event. It depends on ports only (storage, media tool,
// publisher), so it is unit-tested with fakes.
package processor

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"os"
	"path/filepath"
	"time"

	"github.com/tutorcraft/media-worker/internal/video"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/validate"
)

const (
	workspacePattern = "job-*"
	sourceFileName   = "source"
	outputDirName    = "hls"
)

// ErrObjectTooLarge must be returned (wrapped) by ObjectStore.DownloadFile
// when the object exceeds maxBytes.
var ErrObjectTooLarge = errors.New("object too large")

// ErrObjectNotFound must be returned (wrapped) when the source object is missing.
var ErrObjectNotFound = errors.New("object not found")

// ObjectStore reads and writes S3 objects.
type ObjectStore interface {
	DownloadFile(ctx context.Context, bucket, key, path string, maxBytes int64) error
	UploadFile(ctx context.Context, bucket, key, path, contentType string) error
}

// MediaTool probes and transcodes local files.
type MediaTool interface {
	Probe(ctx context.Context, path string) (video.MediaInfo, error)
	Transcode(ctx context.Context, job video.HLSJob) error
}

// EventPublisher publishes envelopes to Kafka.
type EventPublisher interface {
	PublishEvent(ctx context.Context, topic string, env envelope.Envelope) error
}

// Settings configure the processor.
type Settings struct {
	Bucket            string // the only bucket read from and written to
	MaxVideoBytes     int64
	WorkDir           string
	ProcessedTopic    string
	UploadParallelism int
}

// Processor handles tc.media.video-uploaded.v1 events.
type Processor struct {
	store     ObjectStore
	tool      MediaTool
	publisher EventPublisher
	settings  Settings
	log       *slog.Logger
	now       func() time.Time
}

// New creates a Processor.
func New(store ObjectStore, tool MediaTool, publisher EventPublisher, settings Settings, log *slog.Logger) *Processor {
	return &Processor{store: store, tool: tool, publisher: publisher, settings: settings, log: log, now: time.Now}
}

// Handle implements kafkax.Handler. Invalid payloads are permanent errors
// (dead-lettered); bad videos produce status "failed"; infrastructure errors
// are returned for retry.
func (p *Processor) Handle(ctx context.Context, env envelope.Envelope) error {
	upload, err := video.DecodeUploaded(env.Payload)
	if err != nil {
		return retry.Permanent(err)
	}
	if upload.Bucket != p.settings.Bucket {
		return retry.Permanent(&validate.Error{Fields: []validate.FieldError{{Field: "bucket", Problem: "is not allowed"}}})
	}
	log := p.log.With(slog.String("eventId", env.EventID), slog.String("tenantId", env.TenantID),
		slog.String("fileId", upload.FileID))
	result, workDir, err := p.transcode(ctx, upload, log)
	if failure, ok := video.AsUnprocessable(err); ok {
		log.Warn("video processing failed", slog.String("reason", failure.Reason), slog.String("error", err.Error()))
		return p.publish(ctx, env.TenantID, video.Failed(upload.FileID, video.FailureMessage(failure, workDir)))
	}
	if err != nil {
		return err
	}
	log.Info("video processed", slog.Int("durationSec", *result.DurationSec), slog.Int("renditions", len(result.Renditions)))
	return p.publish(ctx, env.TenantID, result)
}

// transcode runs the whole pipeline inside a temporary workspace that is always removed.
func (p *Processor) transcode(ctx context.Context, upload video.Uploaded, log *slog.Logger) (video.Processed, string, error) {
	if upload.SizeBytes > p.settings.MaxVideoBytes {
		return video.Processed{}, "", video.Unprocessable(video.ReasonTooLarge, "", nil)
	}
	workDir, err := os.MkdirTemp(p.settings.WorkDir, workspacePattern)
	if err != nil {
		return video.Processed{}, "", fmt.Errorf("create workspace: %w", err)
	}
	defer func() {
		if err := os.RemoveAll(workDir); err != nil {
			log.Error("workspace cleanup failed", slog.String("error", err.Error()))
		}
	}()
	result, err := p.runPipeline(ctx, upload, workDir, log)
	return result, workDir, err
}

func (p *Processor) runPipeline(ctx context.Context, upload video.Uploaded, workDir string, log *slog.Logger) (video.Processed, error) {
	source := filepath.Join(workDir, sourceFileName)
	if err := p.download(ctx, upload, source); err != nil {
		return video.Processed{}, err
	}
	info, err := p.tool.Probe(ctx, source)
	if err != nil {
		return video.Processed{}, err
	}
	renditions := video.SelectRenditions(info.Height)
	outputDir := filepath.Join(workDir, outputDirName)
	log.Info("transcoding started", slog.Int("sourceHeight", info.Height), slog.Int("renditions", len(renditions)))
	job := video.HLSJob{InputPath: source, OutputDir: outputDir, Renditions: renditions, HasAudio: info.HasAudio}
	if err := p.tool.Transcode(ctx, job); err != nil {
		return video.Processed{}, err
	}
	layout := video.Layout{FileID: upload.FileID}
	if err := p.uploadOutput(ctx, layout, outputDir); err != nil {
		return video.Processed{}, err
	}
	return video.Ready(upload.FileID, layout, roundSeconds(info.DurationSec), renditionInfos(renditions, info.HasAudio)), nil
}

func (p *Processor) download(ctx context.Context, upload video.Uploaded, path string) error {
	err := p.store.DownloadFile(ctx, upload.Bucket, upload.ObjectKey, path, p.settings.MaxVideoBytes)
	switch {
	case errors.Is(err, ErrObjectTooLarge):
		return video.Unprocessable(video.ReasonTooLarge, "", err)
	case errors.Is(err, ErrObjectNotFound):
		return video.Unprocessable(video.ReasonSourceMissing, "", err)
	case err != nil:
		return fmt.Errorf("download source: %w", err)
	}
	return nil
}

func (p *Processor) publish(ctx context.Context, tenantID string, result video.Processed) error {
	env, err := envelope.New(video.TypeVideoProcessed, tenantID, result, p.now())
	if err != nil {
		return err
	}
	return p.publisher.PublishEvent(ctx, p.settings.ProcessedTopic, env)
}

func roundSeconds(seconds float64) int {
	return int(math.Round(seconds))
}

func renditionInfos(renditions []video.Rendition, hasAudio bool) []video.RenditionInfo {
	infos := make([]video.RenditionInfo, len(renditions))
	for i, r := range renditions {
		infos[i] = r.Info(hasAudio)
	}
	return infos
}
