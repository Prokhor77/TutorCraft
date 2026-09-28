package video

import (
	"errors"
	"strings"

	"github.com/tutorcraft/workerkit/textsafe"
)

// MaxErrorRunes bounds the error text in the failed event (core truncates to 500 too).
const MaxErrorRunes = 500

// Failure reasons reported to core-api in the "error" field.
const (
	ReasonTooLarge        = "source video is too large"
	ReasonSourceMissing   = "source video not found in storage"
	ReasonUnreadable      = "source is not a readable video"
	ReasonTranscodeFailed = "transcoding failed"
	ReasonTimedOut        = "transcoding timed out"
)

// UnprocessableError means the input can never be processed: the worker
// reports status "failed" instead of retrying.
type UnprocessableError struct {
	Reason string // short, user-safe
	Detail string // optional tool output, sanitized before publishing
	Err    error
}

func (e *UnprocessableError) Error() string {
	if e.Err == nil {
		return e.Reason
	}
	return e.Reason + ": " + e.Err.Error()
}

func (e *UnprocessableError) Unwrap() error { return e.Err }

// Unprocessable wraps err as unprocessable with a user-safe reason.
func Unprocessable(reason, detail string, err error) error {
	return &UnprocessableError{Reason: reason, Detail: detail, Err: err}
}

// AsUnprocessable extracts an UnprocessableError from err.
func AsUnprocessable(err error) (*UnprocessableError, bool) {
	var target *UnprocessableError
	ok := errors.As(err, &target)
	return target, ok
}

// FailureMessage renders the "error" field: reason plus tool detail with
// local paths removed, single-line and truncated.
func FailureMessage(e *UnprocessableError, workDir string) string {
	message := e.Reason
	if detail := strings.TrimSpace(e.Detail); detail != "" {
		if workDir != "" {
			detail = strings.ReplaceAll(detail, workDir, "")
		}
		message += ": " + detail
	}
	return textsafe.Sanitize(message, MaxErrorRunes)
}
