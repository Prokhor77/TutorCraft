package lifecycle

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestRunCancelsSiblingsOnFailure(t *testing.T) {
	boom := errors.New("boom")
	siblingStopped := make(chan struct{})
	err := Run(context.Background(),
		func(ctx context.Context) error { return boom },
		func(ctx context.Context) error {
			<-ctx.Done()
			close(siblingStopped)
			return nil
		},
	)
	if !errors.Is(err, boom) {
		t.Fatalf("err = %v", err)
	}
	select {
	case <-siblingStopped:
	default:
		t.Fatal("sibling was not stopped")
	}
}

func TestGraceContextOutlivesParentForGracePeriod(t *testing.T) {
	parent, cancelParent := context.WithCancel(context.Background())
	work, cancel := GraceContext(parent, 30*time.Millisecond)
	defer cancel()
	cancelParent()
	select {
	case <-work.Done():
		t.Fatal("work context cancelled immediately")
	case <-time.After(10 * time.Millisecond):
	}
	select {
	case <-work.Done():
	case <-time.After(time.Second):
		t.Fatal("work context not cancelled after grace")
	}
}
