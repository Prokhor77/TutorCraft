// Package kafkax is the Kafka transport shared by the Go workers: an
// at-least-once consumer loop with retries, idempotency and dead-lettering,
// plus an envelope producer.
package kafkax

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/ttlcache"
)

const fetchErrorBackoff = 2 * time.Second

// Reader is the subset of *kafka.Reader used by Consumer (fakes in tests).
type Reader interface {
	FetchMessage(ctx context.Context) (kafka.Message, error)
	CommitMessages(ctx context.Context, msgs ...kafka.Message) error
	Close() error
}

// Handler processes one decoded envelope. Return retry.Permanent for errors
// that will not go away (invalid payload); other errors are retried.
type Handler interface {
	Handle(ctx context.Context, env envelope.Envelope) error
}

// DeadLetterSender publishes messages that could not be processed.
type DeadLetterSender interface {
	Send(ctx context.Context, original kafka.Message, cause error) error
}

// Consumer runs the fetch → handle → commit loop for one reader.
//
// Offsets are committed only after the message was handled or dead-lettered.
// If a message can be neither handled nor dead-lettered, Run returns an error
// without committing, so the message is redelivered after restart.
type Consumer struct {
	Reader      Reader
	Handler     Handler
	DeadLetters DeadLetterSender
	Processed   *ttlcache.Cache[struct{}] // idempotency by eventId
	Retry       retry.Policy
	DLQRetry    retry.Policy
	Log         *slog.Logger
}

// Run consumes until ctx is cancelled. Handling and committing use work,
// which should outlive ctx by a grace period so in-flight messages can finish.
func (c *Consumer) Run(ctx, work context.Context) error {
	for {
		msg, err := c.Reader.FetchMessage(ctx)
		if ctx.Err() != nil {
			return nil
		}
		if err != nil {
			c.Log.Warn("kafka fetch failed", slog.String("error", err.Error()))
			waitOrDone(ctx, fetchErrorBackoff)
			continue
		}
		if err := c.process(work, msg); err != nil {
			return err
		}
		c.commit(work, msg)
	}
}

func (c *Consumer) process(ctx context.Context, msg kafka.Message) error {
	env, err := envelope.Decode(msg.Value)
	if err != nil {
		return c.deadLetter(ctx, msg, err, slog.String("reason", "invalid envelope"))
	}
	log := c.Log.With(slog.String("eventId", env.EventID), slog.String("tenantId", env.TenantID),
		slog.String("type", env.Type))
	if c.Processed.Contains(env.EventID) {
		log.Info("duplicate event skipped")
		return nil
	}
	err = retry.Do(ctx, c.Retry, func(ctx context.Context) error { return c.Handler.Handle(ctx, env) })
	if ctx.Err() != nil {
		return fmt.Errorf("event %s interrupted: %w", env.EventID, ctx.Err())
	}
	if err != nil {
		if dlqErr := c.deadLetter(ctx, msg, err, slog.String("eventId", env.EventID)); dlqErr != nil {
			return dlqErr
		}
	}
	c.Processed.Put(env.EventID, struct{}{})
	return nil
}

func (c *Consumer) deadLetter(ctx context.Context, msg kafka.Message, cause error, attrs ...any) error {
	c.Log.Error("message dead-lettered", append(attrs,
		slog.String("topic", msg.Topic), slog.Int64("offset", msg.Offset), slog.String("error", cause.Error()))...)
	err := retry.Do(ctx, c.DLQRetry, func(ctx context.Context) error { return c.DeadLetters.Send(ctx, msg, cause) })
	if err != nil {
		return fmt.Errorf("dead-letter offset %d of %s: %w", msg.Offset, msg.Topic, err)
	}
	return nil
}

// commit failures are logged only: the message will be redelivered (and
// deduplicated) or a later commit on the same partition supersedes it.
func (c *Consumer) commit(ctx context.Context, msg kafka.Message) {
	if err := c.Reader.CommitMessages(ctx, msg); err != nil && !errors.Is(err, context.Canceled) {
		c.Log.Warn("kafka commit failed", slog.String("topic", msg.Topic),
			slog.Int64("offset", msg.Offset), slog.String("error", err.Error()))
	}
}

func waitOrDone(ctx context.Context, d time.Duration) {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
	case <-timer.C:
	}
}
