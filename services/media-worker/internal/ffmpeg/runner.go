// Package ffmpeg runs the ffmpeg/ffprobe binaries (exec without a shell) and
// maps their failures to domain errors.
package ffmpeg

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"time"

	"github.com/tutorcraft/media-worker/internal/video"
)

const (
	probeTimeout     = time.Minute
	stderrTailBytes  = 4 << 10
	killGracePeriod  = 5 * time.Second
	outputDirPerm    = 0o700
	maxProbeOutBytes = 1 << 20
)

// Runner implements probing and HLS transcoding with local binaries.
type Runner struct {
	FFmpegPath       string
	FFprobePath      string
	TranscodeTimeout time.Duration
}

// Probe inspects a local file. Unreadable input is reported as unprocessable.
func (r Runner) Probe(ctx context.Context, path string) (video.MediaInfo, error) {
	stdout, err := r.run(ctx, probeTimeout, r.FFprobePath, video.FFprobeArgs(path))
	if err != nil {
		return video.MediaInfo{}, classify(ctx, err, video.ReasonUnreadable)
	}
	info, err := video.ParseProbe(stdout)
	if err != nil {
		return video.MediaInfo{}, video.Unprocessable(video.ReasonUnreadable, "", err)
	}
	return info, nil
}

// Transcode produces the HLS output of job.
func (r Runner) Transcode(ctx context.Context, job video.HLSJob) error {
	if err := os.MkdirAll(job.OutputDir, outputDirPerm); err != nil {
		return fmt.Errorf("create output dir: %w", err)
	}
	_, err := r.run(ctx, r.TranscodeTimeout, r.FFmpegPath, video.FFmpegArgs(job))
	if err != nil {
		return classify(ctx, err, video.ReasonTranscodeFailed)
	}
	return nil
}

// ExecError is a failed tool run.
type ExecError struct {
	Tool       string
	TimedOut   bool
	StderrTail string
	Err        error
}

func (e *ExecError) Error() string {
	return fmt.Sprintf("%s failed: %v", e.Tool, e.Err)
}

func (e *ExecError) Unwrap() error { return e.Err }

func (r Runner) run(ctx context.Context, timeout time.Duration, bin string, args []string) ([]byte, error) {
	runCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	cmd := exec.CommandContext(runCtx, bin, args...)
	cmd.WaitDelay = killGracePeriod
	var stdout bytes.Buffer
	stderr := &tailBuffer{limit: stderrTailBytes}
	cmd.Stdout = &limitedBuffer{buf: &stdout, limit: maxProbeOutBytes}
	cmd.Stderr = stderr
	if err := cmd.Run(); err != nil {
		timedOut := errors.Is(runCtx.Err(), context.DeadlineExceeded) && ctx.Err() == nil
		return nil, &ExecError{Tool: bin, TimedOut: timedOut, StderrTail: stderr.String(), Err: err}
	}
	return stdout.Bytes(), nil
}

// classify decides whether a tool failure is the input's fault (unprocessable)
// or the environment's (returned as-is, retried): a missing binary or a
// cancelled parent context are never the input's fault.
func classify(ctx context.Context, err error, reason string) error {
	var execErr *ExecError
	if ctx.Err() != nil || errors.Is(err, exec.ErrNotFound) || !errors.As(err, &execErr) {
		return err
	}
	var exitErr *exec.ExitError
	if !execErr.TimedOut && !errors.As(err, &exitErr) {
		return err // failed to start (permissions etc.)
	}
	if execErr.TimedOut {
		return video.Unprocessable(video.ReasonTimedOut, "", err)
	}
	return video.Unprocessable(reason, execErr.StderrTail, err)
}
