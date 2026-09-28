// Package lifecycle runs a service's long-lived components and coordinates
// graceful shutdown.
package lifecycle

import (
	"context"
	"errors"
	"net/http"
	"sync"
	"time"
)

// Component runs until ctx is cancelled (returning nil) or fails (returning an error).
type Component func(ctx context.Context) error

// Run starts all components; the first failure cancels the others.
// It returns after every component has returned.
func Run(ctx context.Context, components ...Component) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	var (
		wg       sync.WaitGroup
		once     sync.Once
		firstErr error
	)
	for _, component := range components {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := component(ctx); err != nil {
				once.Do(func() { firstErr = err })
				cancel()
			}
		}()
	}
	wg.Wait()
	return firstErr
}

// GraceContext returns a context that is cancelled grace after parent is done.
// Use it for in-flight work that should be allowed to finish during shutdown.
func GraceContext(parent context.Context, grace time.Duration) (context.Context, context.CancelFunc) {
	ctx, cancel := context.WithCancel(context.WithoutCancel(parent))
	go func() {
		select {
		case <-ctx.Done():
			return
		case <-parent.Done():
		}
		timer := time.NewTimer(grace)
		defer timer.Stop()
		select {
		case <-ctx.Done():
		case <-timer.C:
			cancel()
		}
	}()
	return ctx, cancel
}

// HTTPServer returns a Component that serves srv until ctx is cancelled and
// then shuts it down, waiting at most shutdownTimeout. beforeShutdown (optional)
// runs first, e.g. to flip readiness.
func HTTPServer(srv *http.Server, shutdownTimeout time.Duration, beforeShutdown func()) Component {
	return func(ctx context.Context) error {
		serveErr := make(chan error, 1)
		go func() { serveErr <- srv.ListenAndServe() }()
		select {
		case err := <-serveErr:
			return err
		case <-ctx.Done():
		}
		if beforeShutdown != nil {
			beforeShutdown()
		}
		shutdownCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), shutdownTimeout)
		defer cancel()
		if err := srv.Shutdown(shutdownCtx); err != nil {
			return err
		}
		if err := <-serveErr; !errors.Is(err, http.ErrServerClosed) {
			return err
		}
		return nil
	}
}
