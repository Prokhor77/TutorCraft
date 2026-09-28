package kafkax

import (
	"time"

	"github.com/segmentio/kafka-go"
)

const (
	readerMinBytes      = 1
	readerMaxBytes      = 10 << 20
	readerMaxWait       = time.Second
	writerBatchTimeout  = 10 * time.Millisecond
	writerWriteTimeout  = 10 * time.Second
	writerMaxAttempts   = 3
	syncCommitsInterval = 0
)

// NewReader creates a consumer-group reader with synchronous commits, so that
// CommitMessages returns only after the broker stored the offset.
func NewReader(brokers []string, groupID, topic string) *kafka.Reader {
	return kafka.NewReader(kafka.ReaderConfig{
		Brokers:        brokers,
		GroupID:        groupID,
		Topic:          topic,
		MinBytes:       readerMinBytes,
		MaxBytes:       readerMaxBytes,
		MaxWait:        readerMaxWait,
		CommitInterval: syncCommitsInterval,
		StartOffset:    kafka.FirstOffset,
	})
}

// NewWriter creates a writer that partitions by key with the same murmur2
// hashing as the Java client, so events of one tenant keep their order.
func NewWriter(brokers []string) *kafka.Writer {
	return &kafka.Writer{
		Addr:                   kafka.TCP(brokers...),
		Balancer:               &kafka.Murmur2Balancer{},
		RequiredAcks:           kafka.RequireAll,
		BatchTimeout:           writerBatchTimeout,
		WriteTimeout:           writerWriteTimeout,
		MaxAttempts:            writerMaxAttempts,
		AllowAutoTopicCreation: false,
	}
}
