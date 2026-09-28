package kafkax

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/logging"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/ttlcache"
)

const (
	topic    = "tc.test.v1"
	tenantID = "0192f3c1-0000-7000-8000-000000000001"
)

// fakeReader serves queued messages, then signals drained and blocks until cancelled.
type fakeReader struct {
	mu        sync.Mutex
	queue     []kafka.Message
	committed []int64
	drained   chan struct{}
}

func newFakeReader(msgs ...kafka.Message) *fakeReader {
	return &fakeReader{queue: msgs, drained: make(chan struct{})}
}

func (r *fakeReader) FetchMessage(ctx context.Context) (kafka.Message, error) {
	r.mu.Lock()
	if len(r.queue) > 0 {
		msg := r.queue[0]
		r.queue = r.queue[1:]
		r.mu.Unlock()
		return msg, nil
	}
	r.mu.Unlock()
	select {
	case <-r.drained:
	default:
		close(r.drained)
	}
	<-ctx.Done()
	return kafka.Message{}, ctx.Err()
}

func (r *fakeReader) CommitMessages(_ context.Context, msgs ...kafka.Message) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, m := range msgs {
		r.committed = append(r.committed, m.Offset)
	}
	return nil
}

func (r *fakeReader) Close() error { return nil }

type fakeWriter struct {
	mu   sync.Mutex
	msgs []kafka.Message
	err  error
}

func (w *fakeWriter) WriteMessages(_ context.Context, msgs ...kafka.Message) error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.err != nil {
		return w.err
	}
	w.msgs = append(w.msgs, msgs...)
	return nil
}

func (w *fakeWriter) Close() error { return nil }

type handlerFunc func(ctx context.Context, env envelope.Envelope) error

func (f handlerFunc) Handle(ctx context.Context, env envelope.Envelope) error { return f(ctx, env) }

func eventMessage(t *testing.T, offset int64, eventID string) kafka.Message {
	t.Helper()
	env, err := envelope.New("test.event", tenantID, map[string]string{"k": "v"}, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	if eventID != "" {
		env.EventID = eventID
	}
	value, err := env.Encode()
	if err != nil {
		t.Fatal(err)
	}
	return kafka.Message{Topic: topic, Offset: offset, Key: []byte(tenantID), Value: value}
}

func fastPolicy() retry.Policy {
	return retry.Policy{Attempts: 3, InitialDelay: time.Millisecond, MaxDelay: time.Millisecond, Multiplier: 1}
}

func newConsumer(reader Reader, writer *fakeWriter, h Handler) *Consumer {
	return &Consumer{
		Reader: reader, Handler: h,
		DeadLetters: NewDeadLetters(NewProducer(writer)),
		Processed:   ttlcache.New[struct{}](100, time.Hour),
		Retry:       fastPolicy(), DLQRetry: fastPolicy(), Log: logging.Discard(),
	}
}

func runUntilDrained(t *testing.T, c *Consumer, reader *fakeReader) error {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- c.Run(ctx, context.Background()) }()
	select {
	case <-reader.drained:
		cancel()
	case err := <-done:
		cancel()
		return err
	case <-time.After(5 * time.Second):
		cancel()
		t.Fatal("consumer did not drain")
	}
	return <-done
}

func TestConsumerHandlesCommitsAndDeduplicates(t *testing.T) {
	const eventID = "0192f3c1-7b2a-7c3d-8e4f-123456789abc"
	reader := newFakeReader(eventMessage(t, 1, eventID), eventMessage(t, 2, eventID))
	calls := 0
	c := newConsumer(reader, &fakeWriter{}, handlerFunc(func(context.Context, envelope.Envelope) error {
		calls++
		return nil
	}))
	if err := runUntilDrained(t, c, reader); err != nil {
		t.Fatal(err)
	}
	if calls != 1 || len(reader.committed) != 2 {
		t.Fatalf("calls=%d committed=%v", calls, reader.committed)
	}
}

func TestConsumerRetriesTransientThenDeadLetters(t *testing.T) {
	reader := newFakeReader(eventMessage(t, 7, ""))
	writer := &fakeWriter{}
	calls := 0
	c := newConsumer(reader, writer, handlerFunc(func(context.Context, envelope.Envelope) error {
		calls++
		return errors.New("s3 unavailable")
	}))
	if err := runUntilDrained(t, c, reader); err != nil {
		t.Fatal(err)
	}
	if calls != 3 || len(writer.msgs) != 1 || len(reader.committed) != 1 {
		t.Fatalf("calls=%d dlq=%d committed=%v", calls, len(writer.msgs), reader.committed)
	}
	dlq := writer.msgs[0]
	if dlq.Topic != topic+DeadLetterSuffix || headerValue(dlq, HeaderError) != "s3 unavailable" ||
		headerValue(dlq, HeaderOriginalOffset) != "7" {
		t.Fatalf("unexpected DLQ message %+v", dlq)
	}
}

func TestConsumerDeadLettersInvalidEnvelopeAndPermanentErrors(t *testing.T) {
	invalid := kafka.Message{Topic: topic, Offset: 1, Value: []byte(`{"version":2}`)}
	reader := newFakeReader(invalid, eventMessage(t, 2, ""))
	writer := &fakeWriter{}
	calls := 0
	c := newConsumer(reader, writer, handlerFunc(func(context.Context, envelope.Envelope) error {
		calls++
		return retry.Permanent(errors.New("bad payload"))
	}))
	if err := runUntilDrained(t, c, reader); err != nil {
		t.Fatal(err)
	}
	if calls != 1 || len(writer.msgs) != 2 || len(reader.committed) != 2 {
		t.Fatalf("calls=%d dlq=%d committed=%v", calls, len(writer.msgs), reader.committed)
	}
}

func TestConsumerStopsWithoutCommitWhenDeadLetterFails(t *testing.T) {
	reader := newFakeReader(eventMessage(t, 1, ""))
	writer := &fakeWriter{err: errors.New("broker down")}
	c := newConsumer(reader, writer, handlerFunc(func(context.Context, envelope.Envelope) error {
		return retry.Permanent(errors.New("bad payload"))
	}))
	if err := runUntilDrained(t, c, reader); err == nil {
		t.Fatal("expected fatal error")
	}
	if len(reader.committed) != 0 {
		t.Fatalf("must not commit, got %v", reader.committed)
	}
}

func headerValue(msg kafka.Message, key string) string {
	for _, h := range msg.Headers {
		if h.Key == key {
			return string(h.Value)
		}
	}
	return ""
}
