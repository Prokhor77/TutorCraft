// Package delivery routes a requested notification to its channels and
// reports one tc.notify.delivered.v1 event per channel.
package delivery

import (
	"context"
	"errors"

	"github.com/tutorcraft/notifier/internal/notification"
)

// Request is a notification together with the tenant it belongs to.
type Request struct {
	TenantID     string
	Notification notification.Requested
}

// Sender delivers to one channel.
//
// Return Skip(reason) when the channel does not apply (no address, not
// configured, recipient offline), retry.Permanent for errors that will not go
// away, retry.After for rate limits, and plain errors for transient failures.
type Sender interface {
	Channel() notification.Channel
	Send(ctx context.Context, req Request) error
}

// SkipError marks a channel as not applicable.
type SkipError struct {
	Reason string
}

func (e *SkipError) Error() string { return e.Reason }

// Skip returns a SkipError.
func Skip(reason string) error {
	return &SkipError{Reason: reason}
}

func asSkip(err error) (*SkipError, bool) {
	var skip *SkipError
	ok := errors.As(err, &skip)
	return skip, ok
}
