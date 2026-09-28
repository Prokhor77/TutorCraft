// Package localfs implements processor.ObjectStore on a local directory: an
// object key is a relative path under Root. It is used while files live on
// the server's disk (STORAGE_DRIVER=local) in a volume shared with core-api,
// which serves them to browsers. The bucket argument is ignored: the
// processor already checks it against the configured logical bucket.
package localfs

import (
	"context"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"strings"

	"github.com/tutorcraft/media-worker/internal/processor"
)

const (
	dirMode        = 0o755
	fileMode       = 0o644
	partialPattern = ".part-*"
)

// ErrInvalidKey is returned for keys that are absolute, empty or escape Root.
var ErrInvalidKey = errors.New("invalid object key")

// Store reads and writes objects under Root.
type Store struct {
	Root string
}

// DownloadFile copies the object to dst, failing with processor.ErrObjectTooLarge
// when it exceeds maxBytes and processor.ErrObjectNotFound when it is missing.
func (s Store) DownloadFile(ctx context.Context, _, key, dst string, maxBytes int64) error {
	src, err := s.resolve(key)
	if err != nil {
		return err
	}
	in, err := os.Open(src)
	if errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("%w: %s", processor.ErrObjectNotFound, key)
	}
	if err != nil {
		return fmt.Errorf("open object: %w", err)
	}
	defer in.Close()
	if err := tooLarge(in, maxBytes); err != nil {
		return err
	}
	return copyFile(ctx, in, dst)
}

// UploadFile stores the local file at key atomically (temp file + rename), so
// readers never see a partially written object. contentType is not stored:
// core-api derives it from the file extension.
func (s Store) UploadFile(ctx context.Context, _, key, src, _ string) error {
	dst, err := s.resolve(key)
	if err != nil {
		return err
	}
	in, err := os.Open(src)
	if err != nil {
		return fmt.Errorf("open upload source: %w", err)
	}
	defer in.Close()
	if err := os.MkdirAll(filepath.Dir(dst), dirMode); err != nil {
		return fmt.Errorf("create object directory: %w", err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(dst), filepath.Base(dst)+partialPattern)
	if err != nil {
		return fmt.Errorf("create temp object: %w", err)
	}
	defer os.Remove(tmp.Name()) // no-op after a successful rename
	if err := writeAndClose(ctx, tmp, in); err != nil {
		return err
	}
	if err := os.Chmod(tmp.Name(), fileMode); err != nil {
		return fmt.Errorf("chmod object: %w", err)
	}
	if err := os.Rename(tmp.Name(), dst); err != nil {
		return fmt.Errorf("publish object: %w", err)
	}
	return nil
}

// resolve maps a key to a path under Root, rejecting traversal.
func (s Store) resolve(key string) (string, error) {
	clean := path.Clean(key)
	if key == "" || path.IsAbs(key) || clean != key || clean == ".." || strings.HasPrefix(clean, "../") {
		return "", fmt.Errorf("%w: %q", ErrInvalidKey, key)
	}
	return filepath.Join(s.Root, filepath.FromSlash(clean)), nil
}

func tooLarge(in *os.File, maxBytes int64) error {
	info, err := in.Stat()
	if err != nil {
		return fmt.Errorf("stat object: %w", err)
	}
	if info.Size() > maxBytes {
		return fmt.Errorf("%w: %d > %d bytes", processor.ErrObjectTooLarge, info.Size(), maxBytes)
	}
	return nil
}

func copyFile(ctx context.Context, in io.Reader, dst string) error {
	out, err := os.Create(dst)
	if err != nil {
		return fmt.Errorf("create %s: %w", filepath.Base(dst), err)
	}
	return writeAndClose(ctx, out, in)
}

func writeAndClose(ctx context.Context, out *os.File, in io.Reader) error {
	_, copyErr := io.Copy(out, contextReader{ctx: ctx, r: in})
	closeErr := out.Close()
	if copyErr != nil {
		return fmt.Errorf("copy object: %w", copyErr)
	}
	if closeErr != nil {
		return fmt.Errorf("close object: %w", closeErr)
	}
	return nil
}

// contextReader stops a long copy when the job is cancelled.
type contextReader struct {
	ctx context.Context
	r   io.Reader
}

func (c contextReader) Read(p []byte) (int, error) {
	if err := c.ctx.Err(); err != nil {
		return 0, err
	}
	return c.r.Read(p)
}
