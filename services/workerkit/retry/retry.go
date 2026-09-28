// Package retry runs operations with exponential backoff.
//
// Errors wrapped with Permanent stop retrying immediately. Errors wrapped with
// After carry a server-provided delay hint (e.g. Telegram retry_after).
package retry

import (
	"context"
	"errors"
	"time"
)

const (
	defaultAttempts     = 3
	defaultInitialDelay = 500 * time.Millisecond
	defaultMaxDelay     = 10 * time.Second
	defaultMultiplier   = 2
)

// Policy configures retries. Attempts counts the first call too.
type Policy struct {
	Attempts     int
	InitialDelay time.Duration
	MaxDelay     time.Duration
	Multiplier   float64
}

// DefaultPolicy is 3 attempts with 0.5s, 1s backoff (capped at 10s).
func DefaultPolicy() Policy {
	return Policy{
		Attempts:     defaultAttempts,
		InitialDelay: defaultInitialDelay,
		MaxDelay:     defaultMaxDelay,
		Multiplier:   defaultMultiplier,
	}
}

type permanentError struct{ err error }

func (e *permanentError) Error() string { return e.err.Error() }
func (e *permanentError) Unwrap() error { return e.err }

// Permanent marks err as not worth retrying.
func Permanent(err error) error {
	if err == nil {
		return nil
	}
	return &permanentError{err: err}
}

// IsPermanent reports whether err (or any error it wraps) was marked Permanent.
func IsPermanent(err error) bool {
	var p *permanentError
	return errors.As(err, &p)
}

type afterError struct {
	err   error
	delay time.Duration
}

func (e *afterError) Error() string { return e.err.Error() }
func (e *afterError) Unwrap() error { return e.err }

// After marks err as retryable no sooner than delay.
func After(delay time.Duration, err error) error {
	if err == nil {
		return nil
	}
	return &afterError{err: err, delay: delay}
}

// Do calls op until it succeeds, returns a permanent error, attempts run out,
// or ctx is cancelled. It returns the last error from op.
func Do(ctx context.Context, p Policy, op func(ctx context.Context) error) error {
	for attempt := 1; ; attempt++ {
		err := op(ctx)
		if err == nil || IsPermanent(err) || attempt >= p.Attempts {
			return err
		}
		delay, ok := p.delay(attempt, err)
		if !ok {
			return err
		}
		if waitErr := sleep(ctx, delay); waitErr != nil {
			return errors.Join(err, waitErr)
		}
	}
}

// delay returns the wait before the next attempt; ok is false when a server
// hint asks for a longer wait than MaxDelay (give up rather than block).
func (p Policy) delay(attempt int, err error) (time.Duration, bool) {
	var hinted *afterError
	if errors.As(err, &hinted) {
		return hinted.delay, hinted.delay <= p.MaxDelay
	}
	d := float64(p.InitialDelay)
	for i := 1; i < attempt; i++ {
		d *= p.Multiplier
	}
	return min(time.Duration(d), p.MaxDelay), true
}

func sleep(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
