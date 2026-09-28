package s3

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

// fakeS3 stores objects in memory and checks that requests are signed.
type fakeS3 struct {
	mu           sync.Mutex
	objects      map[string][]byte
	contentTypes map[string]string
}

func newFakeS3() *fakeS3 {
	return &fakeS3{objects: map[string][]byte{}, contentTypes: map[string]string{}}
}

func (f *fakeS3) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !strings.HasPrefix(r.Header.Get(HeaderAuthorization), algorithm+" Credential=ak/") {
		http.Error(w, "<Error><Code>AccessDenied</Code></Error>", http.StatusForbidden)
		return
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	switch r.Method {
	case http.MethodPut:
		body, _ := io.ReadAll(r.Body)
		f.objects[r.URL.Path] = body
		f.contentTypes[r.URL.Path] = r.Header.Get(headerContentType)
	case http.MethodGet:
		body, ok := f.objects[r.URL.Path]
		if !ok {
			w.WriteHeader(http.StatusNotFound)
			_, _ = io.WriteString(w, "<Error><Code>NoSuchKey</Code></Error>")
			return
		}
		_, _ = w.Write(body)
	}
}

func newTestClient(t *testing.T, handler http.Handler) *Client {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	endpoint, err := url.Parse(server.URL)
	if err != nil {
		t.Fatal(err)
	}
	return NewClient(Config{Endpoint: endpoint, Region: "us-east-1", Credentials: Credentials{"ak", "sk"}})
}

func TestUploadThenDownloadRoundTrip(t *testing.T) {
	fake := newFakeS3()
	client := newTestClient(t, fake)
	dir := t.TempDir()
	src := filepath.Join(dir, "seg.ts")
	if err := os.WriteFile(src, []byte("segment-bytes"), 0o600); err != nil {
		t.Fatal(err)
	}
	ctx := context.Background()
	if err := client.UploadFile(ctx, "bucket", "hls/f 1/seg.ts", src, "video/mp2t"); err != nil {
		t.Fatalf("upload: %v", err)
	}
	if fake.contentTypes["/bucket/hls/f 1/seg.ts"] != "video/mp2t" {
		t.Fatalf("content type not stored: %v", fake.contentTypes)
	}
	dst := filepath.Join(dir, "out")
	if err := client.DownloadFile(ctx, "bucket", "hls/f 1/seg.ts", dst, 100); err != nil {
		t.Fatalf("download: %v", err)
	}
	if got, _ := os.ReadFile(dst); string(got) != "segment-bytes" {
		t.Fatalf("downloaded %q", got)
	}
}

func TestDownloadRejectsOversizedObject(t *testing.T) {
	fake := newFakeS3()
	fake.objects["/bucket/big"] = []byte("0123456789")
	client := newTestClient(t, fake)
	err := client.DownloadFile(context.Background(), "bucket", "big", filepath.Join(t.TempDir(), "x"), 5)
	if !errors.Is(err, ErrObjectTooLarge) {
		t.Fatalf("want ErrObjectTooLarge, got %v", err)
	}
}

func TestDownloadRejectsOversizedStreamWithoutContentLength(t *testing.T) {
	client := newTestClient(t, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.(http.Flusher).Flush() // forces chunked encoding: no Content-Length
		_, _ = io.WriteString(w, "0123456789")
	}))
	err := client.DownloadFile(context.Background(), "bucket", "k", filepath.Join(t.TempDir(), "x"), 5)
	if !errors.Is(err, ErrObjectTooLarge) {
		t.Fatalf("want ErrObjectTooLarge, got %v", err)
	}
}

func TestDownloadMissingObjectIsNotFound(t *testing.T) {
	client := newTestClient(t, newFakeS3())
	err := client.DownloadFile(context.Background(), "bucket", "missing", filepath.Join(t.TempDir(), "x"), 5)
	var statusErr *StatusError
	if !IsNotFound(err) || !errors.As(err, &statusErr) || statusErr.Code != "NoSuchKey" {
		t.Fatalf("want NoSuchKey 404, got %v", err)
	}
}

func TestObjectURLIsPathStyleAndEncoded(t *testing.T) {
	endpoint, _ := url.Parse("http://minio:9000")
	client := NewClient(Config{Endpoint: endpoint})
	if got := client.objectURL("tutorcraft", "t/a b+c.mp4").String(); got != "http://minio:9000/tutorcraft/t/a%20b%2Bc.mp4" {
		t.Fatalf("url = %s", got)
	}
}
