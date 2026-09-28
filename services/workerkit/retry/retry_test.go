package retry

import (
	"context"
	"errors"
	"testing"
	"time"
)

var errTransient = errors.New("transient")

func fastPolicy() Policy {
	return Policy{Attempts: 3, InitialDelay: time.Millisecond, MaxDelay: 50 * time.Millisecond, Multiplier: 2}
}

func TestDoRetriesUntilSuccess(t *testing.T) {
	calls := 0
	err := Do(context.Background(), fastPolicy(), func(context.Context) error {
		calls++
		if calls < 3 {
			return errTransient
		}
		return nil
	})
	if err != nil || calls != 3 {
		t.Fatalf("err=%v calls=%d", err, calls)
	}
}

func TestDoStopsAfterAttempts(t *testing.T) {
	calls := 0
	err := Do(context.Background(), fastPolicy(), func(context.Context) error {
		calls++
		return errTransient
	})
	if !errors.Is(err, errTransient) || calls != 3 {
		t.Fatalf("err=%v calls=%d", err, calls)
	}
}

func TestDoDoesNotRetryPermanent(t *testing.T) {
	calls := 0
	err := Do(context.Background(), fastPolicy(), func(context.Context) error {
		calls++
		return Permanent(errTransient)
	})
	if !IsPermanent(err) || !errors.Is(err, errTransient) || calls != 1 {
		t.Fatalf("err=%v calls=%d", err, calls)
	}
}

func TestDoHonoursRetryAfterHint(t *testing.T) {
	calls := 0
	start := time.Now()
	err := Do(context.Background(), fastPolicy(), func(context.Context) error {
		calls++
		if calls == 1 {
			return After(30*time.Millisecond, errTransient)
		}
		return nil
	})
	if err != nil || time.Since(start) < 30*time.Millisecond {
		t.Fatalf("hint not honoured: err=%v elapsed=%v", err, time.Since(start))
	}
}

func TestDoGivesUpWhenHintExceedsMaxDelay(t *testing.T) {
	calls := 0
	err := Do(context.Background(), fastPolicy(), func(context.Context) error {
		calls++
		return After(time.Hour, errTransient)
	})
	if err == nil || calls != 1 {
		t.Fatalf("err=%v calls=%d", err, calls)
	}
}

func TestDoStopsOnContextCancel(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	policy := fastPolicy()
	policy.InitialDelay = time.Hour
	policy.MaxDelay = time.Hour
	err := Do(ctx, policy, func(context.Context) error { return errTransient })
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("want context.Canceled, got %v", err)
	}
}

func TestBackoffGrowsAndIsCapped(t *testing.T) {
	p := Policy{Attempts: 10, InitialDelay: 100 * time.Millisecond, MaxDelay: 300 * time.Millisecond, Multiplier: 2}
	want := []time.Duration{100 * time.Millisecond, 200 * time.Millisecond, 300 * time.Millisecond}
	for i, w := range want {
		if got, _ := p.delay(i+1, errTransient); got != w {
			t.Errorf("attempt %d: delay %v, want %v", i+1, got, w)
		}
	}
}
