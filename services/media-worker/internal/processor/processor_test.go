package processor

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/tutorcraft/media-worker/internal/video"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/logging"
	"github.com/tutorcraft/workerkit/retry"
)

const (
	fileID       = "0192f3c1-7b2a-7c3d-8e4f-123456789abc"
	ownerID      = "0192f3c1-0000-7000-8000-000000000002"
	tenantID     = "0192f3c1-0000-7000-8000-000000000001"
	bucket       = "tutorcraft"
	topicOut     = "tc.media.video-processed.v1"
	maxBytes     = 1 << 20
	sourceObject = "t/tenant/f/original.mp4"
)

type fakeStore struct {
	mu          sync.Mutex
	downloadErr error
	uploadErr   error
	uploads     map[string]string // key -> content type
	order       []string
}

func (f *fakeStore) DownloadFile(_ context.Context, _, _, path string, _ int64) error {
	if f.downloadErr != nil {
		return f.downloadErr
	}
	return os.WriteFile(path, []byte("video"), 0o600)
}

func (f *fakeStore) UploadFile(_ context.Context, _, key, _, contentType string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.uploadErr != nil {
		return f.uploadErr
	}
	f.uploads[key] = contentType
	f.order = append(f.order, key)
	return nil
}

// fakeTool pretends to transcode by writing the expected HLS files.
type fakeTool struct {
	info         video.MediaInfo
	probeErr     error
	transcodeErr error
	job          video.HLSJob
	workDirs     []string
}

func (f *fakeTool) Probe(_ context.Context, path string) (video.MediaInfo, error) {
	f.workDirs = append(f.workDirs, filepath.Dir(path))
	return f.info, f.probeErr
}

func (f *fakeTool) Transcode(_ context.Context, job video.HLSJob) error {
	f.job = job
	if f.transcodeErr != nil {
		return f.transcodeErr
	}
	for _, r := range job.Renditions {
		dir := filepath.Join(job.OutputDir, r.Name)
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return err
		}
		for _, name := range []string{video.VariantPlaylistName, "seg_00000.ts", "seg_00001.ts"} {
			if err := os.WriteFile(filepath.Join(dir, name), []byte("x"), 0o600); err != nil {
				return err
			}
		}
	}
	return os.WriteFile(filepath.Join(job.OutputDir, video.MasterPlaylistName), []byte("m"), 0o600)
}

type fakePublisher struct {
	events []envelope.Envelope
	topics []string
	err    error
}

func (f *fakePublisher) PublishEvent(_ context.Context, topic string, env envelope.Envelope) error {
	if f.err != nil {
		return f.err
	}
	f.events = append(f.events, env)
	f.topics = append(f.topics, topic)
	return nil
}

type fixture struct {
	store     *fakeStore
	tool      *fakeTool
	publisher *fakePublisher
	processor *Processor
}

func newFixture(t *testing.T) *fixture {
	t.Helper()
	f := &fixture{
		store:     &fakeStore{uploads: map[string]string{}},
		tool:      &fakeTool{info: video.MediaInfo{DurationSec: 12.6, Width: 1920, Height: 1080, HasAudio: true}},
		publisher: &fakePublisher{},
	}
	settings := Settings{Bucket: bucket, MaxVideoBytes: maxBytes, WorkDir: t.TempDir(), ProcessedTopic: topicOut,
		UploadParallelism: 3}
	f.processor = New(f.store, f.tool, f.publisher, settings, logging.Discard())
	return f
}

func uploadedEvent(t *testing.T, mutate func(map[string]any)) envelope.Envelope {
	t.Helper()
	payload := map[string]any{"fileId": fileID, "bucket": bucket, "objectKey": sourceObject,
		"contentType": "video/mp4", "sizeBytes": 1000, "ownerUserId": ownerID}
	if mutate != nil {
		mutate(payload)
	}
	env, err := envelope.New("media.video-uploaded", tenantID, payload, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	return env
}

func (f *fixture) processed(t *testing.T) video.Processed {
	t.Helper()
	if len(f.publisher.events) != 1 {
		t.Fatalf("want 1 published event, got %d", len(f.publisher.events))
	}
	env := f.publisher.events[0]
	if env.TenantID != tenantID || env.Type != video.TypeVideoProcessed || f.publisher.topics[0] != topicOut {
		t.Fatalf("unexpected envelope %+v on %s", env, f.publisher.topics[0])
	}
	var result video.Processed
	if err := json.Unmarshal(env.Payload, &result); err != nil {
		t.Fatal(err)
	}
	return result
}

func (f *fixture) assertWorkspaceRemoved(t *testing.T) {
	t.Helper()
	for _, dir := range f.tool.workDirs {
		if _, err := os.Stat(dir); !os.IsNotExist(err) {
			t.Errorf("workspace %s not removed", dir)
		}
	}
}

func TestHandlePublishesReadyWithRenditionsAndUploadsOutput(t *testing.T) {
	f := newFixture(t)
	incoming := uploadedEvent(t, nil)
	if err := f.processor.Handle(context.Background(), incoming); err != nil {
		t.Fatalf("handle: %v", err)
	}
	result := f.processed(t)
	if result.Status != video.StatusReady || *result.DurationSec != 13 || len(result.Renditions) != 2 ||
		result.MasterPlaylistKey != "hls/"+fileID+"/master.m3u8" || result.HLSPrefix != "hls/"+fileID+"/" {
		t.Fatalf("unexpected result %+v", result)
	}
	if f.publisher.events[0].EventID == incoming.EventID {
		t.Fatal("processed event must have a new eventId")
	}
	f.assertUploads(t)
	f.assertWorkspaceRemoved(t)
}

func (f *fixture) assertUploads(t *testing.T) {
	t.Helper()
	prefix := "hls/" + fileID + "/"
	want := map[string]string{prefix + "master.m3u8": video.ContentTypePlaylist}
	for _, name := range []string{"360p", "720p"} {
		want[prefix+name+"/index.m3u8"] = video.ContentTypePlaylist
		want[prefix+name+"/seg_00000.ts"] = video.ContentTypeSegment
		want[prefix+name+"/seg_00001.ts"] = video.ContentTypeSegment
	}
	if fmt.Sprint(sortedMap(f.store.uploads)) != fmt.Sprint(sortedMap(want)) {
		t.Fatalf("uploads\n got %v\nwant %v", sortedMap(f.store.uploads), sortedMap(want))
	}
	if last := f.store.order[len(f.store.order)-1]; last != prefix+"master.m3u8" {
		t.Fatalf("master must be uploaded last, last was %s", last)
	}
}

func sortedMap(m map[string]string) []string {
	out := make([]string, 0, len(m))
	for k, v := range m {
		out = append(out, k+"="+v)
	}
	sort.Strings(out)
	return out
}

func TestHandleLowResolutionSkips720p(t *testing.T) {
	f := newFixture(t)
	f.tool.info = video.MediaInfo{DurationSec: 5, Width: 854, Height: 480}
	if err := f.processor.Handle(context.Background(), uploadedEvent(t, nil)); err != nil {
		t.Fatal(err)
	}
	result := f.processed(t)
	if len(result.Renditions) != 1 || result.Renditions[0].Name != "360p" || result.Renditions[0].Bandwidth != 800_000 {
		t.Fatalf("unexpected renditions %+v", result.Renditions)
	}
	if f.tool.job.HasAudio {
		t.Fatal("job must not map audio when source has none")
	}
}

func TestHandleUnprocessableInputPublishesFailed(t *testing.T) {
	cases := map[string]func(f *fixture){
		"corrupt video":  func(f *fixture) { f.tool.probeErr = video.Unprocessable(video.ReasonUnreadable, "", errors.New("x")) },
		"ffmpeg error":   func(f *fixture) { f.tool.transcodeErr = video.Unprocessable(video.ReasonTranscodeFailed, "bad", nil) },
		"missing object": func(f *fixture) { f.store.downloadErr = fmt.Errorf("%w: 404", ErrObjectNotFound) },
		"object too big": func(f *fixture) { f.store.downloadErr = fmt.Errorf("%w: big", ErrObjectTooLarge) },
	}
	for name, arrange := range cases {
		t.Run(name, func(t *testing.T) {
			f := newFixture(t)
			arrange(f)
			if err := f.processor.Handle(context.Background(), uploadedEvent(t, nil)); err != nil {
				t.Fatalf("handle must succeed after publishing failure, got %v", err)
			}
			result := f.processed(t)
			if result.Status != video.StatusFailed || result.Error == "" || result.DurationSec != nil {
				t.Fatalf("unexpected result %+v", result)
			}
			f.assertWorkspaceRemoved(t)
		})
	}
}

func TestHandleDeclaredSizeOverLimitFailsWithoutDownload(t *testing.T) {
	f := newFixture(t)
	f.store.downloadErr = errors.New("must not be called")
	event := uploadedEvent(t, func(p map[string]any) { p["sizeBytes"] = maxBytes + 1 })
	if err := f.processor.Handle(context.Background(), event); err != nil {
		t.Fatal(err)
	}
	if result := f.processed(t); result.Error != video.ReasonTooLarge {
		t.Fatalf("unexpected result %+v", result)
	}
}

func TestHandleInfrastructureErrorsAreRetryable(t *testing.T) {
	cases := map[string]func(f *fixture){
		"download": func(f *fixture) { f.store.downloadErr = errors.New("connection reset") },
		"upload":   func(f *fixture) { f.store.uploadErr = errors.New("503") },
		"publish":  func(f *fixture) { f.publisher.err = errors.New("broker down") },
	}
	for name, arrange := range cases {
		t.Run(name, func(t *testing.T) {
			f := newFixture(t)
			arrange(f)
			err := f.processor.Handle(context.Background(), uploadedEvent(t, nil))
			if err == nil || retry.IsPermanent(err) {
				t.Fatalf("want retryable error, got %v", err)
			}
			if len(f.publisher.events) != 0 {
				t.Fatal("nothing must be published")
			}
			f.assertWorkspaceRemoved(t)
		})
	}
}

func TestHandleInvalidPayloadIsPermanent(t *testing.T) {
	cases := map[string]func(map[string]any){
		"bad file id":    func(p map[string]any) { p["fileId"] = "nope" },
		"foreign bucket": func(p map[string]any) { p["bucket"] = "someone-else" },
	}
	for name, mutate := range cases {
		f := newFixture(t)
		err := f.processor.Handle(context.Background(), uploadedEvent(t, mutate))
		if !retry.IsPermanent(err) || strings.Contains(err.Error(), "someone-else") {
			t.Errorf("%s: want permanent error without values, got %v", name, err)
		}
	}
}
