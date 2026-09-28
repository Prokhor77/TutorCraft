package kafkax

import (
	"context"
	"strconv"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/tutorcraft/workerkit/textsafe"
)

// Dead-letter conventions: `<topic>.dlq`, original key and value, plus headers.
const (
	DeadLetterSuffix        = ".dlq"
	HeaderError             = "x-error"
	HeaderOriginalTopic     = "x-original-topic"
	HeaderOriginalPartition = "x-original-partition"
	HeaderOriginalOffset    = "x-original-offset"
	HeaderFailedAt          = "x-failed-at"
	maxErrorHeaderRunes     = 1000
)

// MessagePublisher writes raw messages.
type MessagePublisher interface {
	Publish(ctx context.Context, msg kafka.Message) error
}

// DeadLetters republishes unprocessable messages to `<topic>.dlq`.
type DeadLetters struct {
	publisher MessagePublisher
	now       func() time.Time
}

// NewDeadLetters creates a dead-letter publisher.
func NewDeadLetters(publisher MessagePublisher) *DeadLetters {
	return &DeadLetters{publisher: publisher, now: time.Now}
}

// Send publishes the original message with the failure reason in headers.
func (d *DeadLetters) Send(ctx context.Context, original kafka.Message, cause error) error {
	headers := append([]kafka.Header{}, original.Headers...)
	headers = append(headers,
		header(HeaderError, textsafe.Sanitize(cause.Error(), maxErrorHeaderRunes)),
		header(HeaderOriginalTopic, original.Topic),
		header(HeaderOriginalPartition, strconv.Itoa(original.Partition)),
		header(HeaderOriginalOffset, strconv.FormatInt(original.Offset, 10)),
		header(HeaderFailedAt, d.now().UTC().Format(time.RFC3339)),
	)
	return d.publisher.Publish(ctx, kafka.Message{
		Topic:   original.Topic + DeadLetterSuffix,
		Key:     original.Key,
		Value:   original.Value,
		Headers: headers,
	})
}

func header(key, value string) kafka.Header {
	return kafka.Header{Key: key, Value: []byte(value)}
}
