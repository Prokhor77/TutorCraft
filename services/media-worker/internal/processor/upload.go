package processor

import (
	"context"
	"errors"
	"fmt"
	"io/fs"
	"path/filepath"
	"sync"

	"github.com/tutorcraft/media-worker/internal/video"
)

type outputFile struct {
	localPath    string
	relativePath string
}

// uploadOutput uploads variant playlists and segments in parallel, then the
// master playlist last, so a player never sees a master whose variants are missing.
func (p *Processor) uploadOutput(ctx context.Context, layout video.Layout, outputDir string) error {
	files, master, err := listOutput(outputDir)
	if err != nil {
		return err
	}
	if master == nil {
		return video.Unprocessable(video.ReasonTranscodeFailed, "master playlist was not produced", nil)
	}
	if err := p.uploadAll(ctx, layout, files); err != nil {
		return err
	}
	return p.uploadOne(ctx, layout, *master)
}

func listOutput(outputDir string) ([]outputFile, *outputFile, error) {
	var files []outputFile
	var master *outputFile
	err := filepath.WalkDir(outputDir, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil || entry.IsDir() {
			return walkErr
		}
		relative, err := filepath.Rel(outputDir, path)
		if err != nil {
			return err
		}
		file := outputFile{localPath: path, relativePath: relative}
		if relative == video.MasterPlaylistName {
			master = &file
			return nil
		}
		files = append(files, file)
		return nil
	})
	if err != nil {
		return nil, nil, fmt.Errorf("list HLS output: %w", err)
	}
	return files, master, nil
}

// uploadAll uploads files with bounded parallelism; any error cancels the rest.
func (p *Processor) uploadAll(ctx context.Context, layout video.Layout, files []outputFile) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	jobs := make(chan outputFile)
	errs := make(chan error, len(files))
	var wg sync.WaitGroup
	for range max(p.settings.UploadParallelism, 1) {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for file := range jobs {
				if err := p.uploadOne(ctx, layout, file); err != nil {
					errs <- err
					cancel()
				}
			}
		}()
	}
	feed(ctx, jobs, files)
	wg.Wait()
	close(errs)
	return joinErrors(errs)
}

func feed(ctx context.Context, jobs chan<- outputFile, files []outputFile) {
	defer close(jobs)
	for _, file := range files {
		select {
		case <-ctx.Done():
			return
		case jobs <- file:
		}
	}
}

func joinErrors(errs <-chan error) error {
	var all []error
	for err := range errs {
		all = append(all, err)
	}
	return errors.Join(all...) // nil when there were no errors
}

func (p *Processor) uploadOne(ctx context.Context, layout video.Layout, file outputFile) error {
	key := layout.KeyFor(file.relativePath)
	contentType := video.ContentTypeFor(file.relativePath)
	if err := p.store.UploadFile(ctx, p.settings.Bucket, key, file.localPath, contentType); err != nil {
		return fmt.Errorf("upload %s: %w", file.relativePath, err)
	}
	return nil
}
