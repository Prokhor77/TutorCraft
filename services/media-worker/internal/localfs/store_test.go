package localfs

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"

	"github.com/tutorcraft/media-worker/internal/processor"
)

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestDownloadCopiesObject(t *testing.T) {
	store := Store{Root: t.TempDir()}
	writeFile(t, filepath.Join(store.Root, "t", "a", "file"), "video")
	dst := filepath.Join(t.TempDir(), "source")
	if err := store.DownloadFile(context.Background(), "local", "t/a/file", dst, 10); err != nil {
		t.Fatal(err)
	}
	if got, _ := os.ReadFile(dst); string(got) != "video" {
		t.Fatalf("unexpected content %q", got)
	}
}

func TestDownloadTranslatesErrors(t *testing.T) {
	store := Store{Root: t.TempDir()}
	writeFile(t, filepath.Join(store.Root, "big"), "0123456789")
	dst := filepath.Join(t.TempDir(), "x")
	cases := map[string]struct {
		key  string
		want error
	}{
		"not found": {"missing", processor.ErrObjectNotFound},
		"too large": {"big", processor.ErrObjectTooLarge},
		"traversal": {"../etc/passwd", ErrInvalidKey},
		"absolute":  {"/etc/passwd", ErrInvalidKey},
		"unclean":   {"a/../../b", ErrInvalidKey},
	}
	for name, c := range cases {
		if err := store.DownloadFile(context.Background(), "local", c.key, dst, 5); !errors.Is(err, c.want) {
			t.Errorf("%s: want %v, got %v", name, c.want, err)
		}
	}
}

func TestUploadWritesObjectAtomically(t *testing.T) {
	store := Store{Root: t.TempDir()}
	src := filepath.Join(t.TempDir(), "seg.ts")
	writeFile(t, src, "segment")
	key := "hls/f1/720p/seg_00000.ts"
	if err := store.UploadFile(context.Background(), "local", key, src, "video/mp2t"); err != nil {
		t.Fatal(err)
	}
	dst := filepath.Join(store.Root, filepath.FromSlash(key))
	got, err := os.ReadFile(dst)
	if err != nil || string(got) != "segment" {
		t.Fatalf("unexpected object %q, %v", got, err)
	}
	entries, _ := os.ReadDir(filepath.Dir(dst))
	if len(entries) != 1 {
		t.Fatalf("temporary files left behind: %v", entries)
	}
	if info, _ := os.Stat(dst); info.Mode().Perm() != fileMode {
		t.Fatalf("unexpected mode %v", info.Mode().Perm())
	}
}

func TestUploadRejectsTraversal(t *testing.T) {
	store := Store{Root: t.TempDir()}
	src := filepath.Join(t.TempDir(), "x")
	writeFile(t, src, "x")
	if err := store.UploadFile(context.Background(), "local", "../x", src, ""); !errors.Is(err, ErrInvalidKey) {
		t.Fatalf("want ErrInvalidKey, got %v", err)
	}
}

func TestUploadStopsWhenCancelled(t *testing.T) {
	store := Store{Root: t.TempDir()}
	src := filepath.Join(t.TempDir(), "x")
	writeFile(t, src, "x")
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := store.UploadFile(ctx, "local", "hls/x", src, ""); !errors.Is(err, context.Canceled) {
		t.Fatalf("want context.Canceled, got %v", err)
	}
	if _, err := os.Stat(filepath.Join(store.Root, "hls", "x")); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("object must not exist after cancellation: %v", err)
	}
}
