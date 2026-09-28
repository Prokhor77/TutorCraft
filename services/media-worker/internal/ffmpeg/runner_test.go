package ffmpeg

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/tutorcraft/media-worker/internal/video"
)

func TestTailBufferKeepsLastBytes(t *testing.T) {
	b := &tailBuffer{limit: 5}
	_, _ = b.Write([]byte("abc"))
	_, _ = b.Write([]byte("defgh"))
	if b.String() != "defgh" {
		t.Fatalf("tail = %q", b.String())
	}
}

func TestMissingBinaryIsNotUnprocessable(t *testing.T) {
	r := Runner{FFprobePath: "/nonexistent/ffprobe"}
	_, err := r.Probe(context.Background(), "/tmp/whatever")
	if err == nil {
		t.Fatal("expected error")
	}
	if _, ok := video.AsUnprocessable(err); ok {
		t.Fatalf("missing binary must be retryable, got unprocessable: %v", err)
	}
}

func requireFFmpeg(t *testing.T) Runner {
	t.Helper()
	ffmpegPath, err := exec.LookPath("ffmpeg")
	if err != nil {
		t.Skip("ffmpeg not installed")
	}
	ffprobePath, err := exec.LookPath("ffprobe")
	if err != nil {
		t.Skip("ffprobe not installed")
	}
	return Runner{FFmpegPath: ffmpegPath, FFprobePath: ffprobePath, TranscodeTimeout: 2 * time.Minute}
}

// generateSample writes a 2-second 1280x720 clip with a sine-wave audio track.
func generateSample(t *testing.T, runner Runner, path string) {
	t.Helper()
	cmd := exec.Command(runner.FFmpegPath, "-hide_banner", "-loglevel", "error", "-y",
		"-f", "lavfi", "-i", "testsrc=duration=2:size=1280x720:rate=25",
		"-f", "lavfi", "-i", "sine=frequency=440:duration=2",
		"-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", "-f", "mp4", path)
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("generate sample: %v: %s", err, out)
	}
}

func TestIntegrationProbeAndTranscode(t *testing.T) {
	runner := requireFFmpeg(t)
	dir := t.TempDir()
	source := filepath.Join(dir, "source")
	generateSample(t, runner, source)
	ctx := context.Background()

	info, err := runner.Probe(ctx, source)
	if err != nil {
		t.Fatalf("probe: %v", err)
	}
	if info.Height != 720 || !info.HasAudio || info.DurationSec < 1.9 || info.DurationSec > 2.2 {
		t.Fatalf("unexpected info %+v", info)
	}
	out := filepath.Join(dir, "out")
	job := video.HLSJob{InputPath: source, OutputDir: out, Renditions: video.SelectRenditions(info.Height), HasAudio: true}
	if err := runner.Transcode(ctx, job); err != nil {
		t.Fatalf("transcode: %v", err)
	}
	master, err := os.ReadFile(filepath.Join(out, video.MasterPlaylistName))
	if err != nil {
		t.Fatalf("master playlist: %v", err)
	}
	for _, name := range []string{"360p", "720p"} {
		if !strings.Contains(string(master), name+"/"+video.VariantPlaylistName) {
			t.Errorf("master lacks %s: %s", name, master)
		}
		variant, err := os.ReadFile(filepath.Join(out, name, video.VariantPlaylistName))
		if err != nil || !strings.Contains(string(variant), "#EXT-X-PLAYLIST-TYPE:VOD") {
			t.Errorf("variant %s: %v %s", name, err, variant)
		}
		if _, err := os.Stat(filepath.Join(out, name, "seg_00000.ts")); err != nil {
			t.Errorf("segment %s: %v", name, err)
		}
	}
}

func TestIntegrationCorruptInputIsUnprocessable(t *testing.T) {
	runner := requireFFmpeg(t)
	source := filepath.Join(t.TempDir(), "source")
	if err := os.WriteFile(source, []byte("definitely not a video"), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := runner.Probe(context.Background(), source)
	failure, ok := video.AsUnprocessable(err)
	if !ok || failure.Reason != video.ReasonUnreadable {
		t.Fatalf("want unprocessable unreadable, got %v", err)
	}
}

func TestIntegrationTimeoutIsUnprocessable(t *testing.T) {
	runner := requireFFmpeg(t)
	dir := t.TempDir()
	source := filepath.Join(dir, "source")
	generateSample(t, runner, source)
	runner.TranscodeTimeout = time.Millisecond
	job := video.HLSJob{InputPath: source, OutputDir: filepath.Join(dir, "out"), Renditions: video.Ladder, HasAudio: true}
	err := runner.Transcode(context.Background(), job)
	failure, ok := video.AsUnprocessable(err)
	if !ok || failure.Reason != video.ReasonTimedOut {
		t.Fatalf("want timeout, got %v", err)
	}
}

func TestCancelledContextIsNotUnprocessable(t *testing.T) {
	runner := requireFFmpeg(t)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := runner.Probe(ctx, "/nonexistent")
	if _, ok := video.AsUnprocessable(err); ok || err == nil {
		t.Fatalf("cancelled run must be retryable, got %v", err)
	}
}
