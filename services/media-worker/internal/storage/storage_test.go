package storage

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"path/filepath"
	"testing"

	"github.com/tutorcraft/media-worker/internal/processor"
	"github.com/tutorcraft/media-worker/internal/s3"
)

func storeFor(t *testing.T, handler http.HandlerFunc) Store {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	endpoint, _ := url.Parse(server.URL)
	return Store{Client: s3.NewClient(s3.Config{Endpoint: endpoint, Region: "us-east-1",
		Credentials: s3.Credentials{AccessKeyID: "ak", SecretAccessKey: "sk"}})}
}

func TestDownloadTranslatesS3Errors(t *testing.T) {
	cases := map[string]struct {
		handler http.HandlerFunc
		want    error
	}{
		"not found": {func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusNotFound)
			_, _ = io.WriteString(w, "<Error><Code>NoSuchKey</Code></Error>")
		}, processor.ErrObjectNotFound},
		"too large": {func(w http.ResponseWriter, _ *http.Request) {
			_, _ = io.WriteString(w, "0123456789")
		}, processor.ErrObjectTooLarge},
	}
	for name, c := range cases {
		err := storeFor(t, c.handler).DownloadFile(context.Background(), "b", "k", filepath.Join(t.TempDir(), "x"), 5)
		if !errors.Is(err, c.want) {
			t.Errorf("%s: want %v, got %v", name, c.want, err)
		}
	}
}

func TestDownloadPassesThroughOtherErrors(t *testing.T) {
	store := storeFor(t, func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusServiceUnavailable) })
	err := store.DownloadFile(context.Background(), "b", "k", filepath.Join(t.TempDir(), "x"), 5)
	if err == nil || errors.Is(err, processor.ErrObjectNotFound) || errors.Is(err, processor.ErrObjectTooLarge) {
		t.Fatalf("want plain retryable error, got %v", err)
	}
}
