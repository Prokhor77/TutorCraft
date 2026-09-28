// Package storage adapts the S3 client to the processor's ObjectStore port,
// translating S3-specific errors into the port's sentinel errors.
package storage

import (
	"context"
	"errors"
	"fmt"

	"github.com/tutorcraft/media-worker/internal/processor"
	"github.com/tutorcraft/media-worker/internal/s3"
)

// Store implements processor.ObjectStore on top of *s3.Client.
type Store struct {
	Client *s3.Client
}

// DownloadFile downloads an object to path.
func (s Store) DownloadFile(ctx context.Context, bucket, key, path string, maxBytes int64) error {
	err := s.Client.DownloadFile(ctx, bucket, key, path, maxBytes)
	switch {
	case errors.Is(err, s3.ErrObjectTooLarge):
		return fmt.Errorf("%w: %w", processor.ErrObjectTooLarge, err)
	case s3.IsNotFound(err):
		return fmt.Errorf("%w: %w", processor.ErrObjectNotFound, err)
	}
	return err
}

// UploadFile uploads a local file.
func (s Store) UploadFile(ctx context.Context, bucket, key, path, contentType string) error {
	return s.Client.UploadFile(ctx, bucket, key, path, contentType)
}
