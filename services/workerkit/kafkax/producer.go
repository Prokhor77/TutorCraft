package kafkax

import (
	"context"
	"fmt"

	"github.com/segmentio/kafka-go"
	"github.com/tutorcraft/workerkit/envelope"
)

// Writer is the subset of *kafka.Writer used here (fakes in tests).
type Writer interface {
	WriteMessages(ctx context.Context, msgs ...kafka.Message) error
	Close() error
}

// Producer publishes envelopes keyed by tenantId (docs/events/README.md).
type Producer struct {
	writer Writer
}

// NewProducer wraps a writer.
func NewProducer(writer Writer) *Producer {
	return &Producer{writer: writer}
}

// PublishEvent encodes env and writes it to topic with key = tenantId.
func (p *Producer) PublishEvent(ctx context.Context, topic string, env envelope.Envelope) error {
	value, err := env.Encode()
	if err != nil {
		return fmt.Errorf("encode %s event: %w", env.Type, err)
	}
	return p.Publish(ctx, kafka.Message{Topic: topic, Key: []byte(env.TenantID), Value: value})
}

// Publish writes a raw message.
func (p *Producer) Publish(ctx context.Context, msg kafka.Message) error {
	if err := p.writer.WriteMessages(ctx, msg); err != nil {
		return fmt.Errorf("publish to %s: %w", msg.Topic, err)
	}
	return nil
}
