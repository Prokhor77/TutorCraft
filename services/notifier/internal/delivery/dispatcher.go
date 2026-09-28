package delivery

import (
	"context"
	"log/slog"
	"time"

	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/textsafe"
	"github.com/tutorcraft/workerkit/ttlcache"
)

const (
	maxErrorRunes           = 300
	reasonCounterWebOnly    = "counter updates are delivered over web only"
	reasonChannelNotEnabled = "channel is not enabled"
)

// EventPublisher publishes envelopes to Kafka.
type EventPublisher interface {
	PublishEvent(ctx context.Context, topic string, env envelope.Envelope) error
}

// Dispatcher implements kafkax.Handler for tc.notify.requested.v1.
//
// Each channel is retried on its own; its final outcome is cached by
// notificationId+channel, so a redelivered or re-emitted request never sends
// twice on a channel that already finished (FR-NOTIF-04).
type Dispatcher struct {
	senders        map[notification.Channel]Sender
	publisher      EventPublisher
	deliveredTopic string
	outcomes       *ttlcache.Cache[notification.Delivered]
	policy         retry.Policy
	log            *slog.Logger
	now            func() time.Time
}

// Options configure a Dispatcher.
type Options struct {
	Senders        []Sender
	Publisher      EventPublisher
	DeliveredTopic string
	Outcomes       *ttlcache.Cache[notification.Delivered]
	Retry          retry.Policy
	Log            *slog.Logger
}

// NewDispatcher creates a Dispatcher.
func NewDispatcher(opts Options) *Dispatcher {
	senders := make(map[notification.Channel]Sender, len(opts.Senders))
	for _, s := range opts.Senders {
		senders[s.Channel()] = s
	}
	return &Dispatcher{
		senders: senders, publisher: opts.Publisher, deliveredTopic: opts.DeliveredTopic,
		outcomes: opts.Outcomes, policy: opts.Retry, log: opts.Log, now: time.Now,
	}
}

// Handle delivers to every requested channel and publishes the outcomes.
func (d *Dispatcher) Handle(ctx context.Context, env envelope.Envelope) error {
	requested, err := notification.Decode(env.Payload)
	if err != nil {
		return retry.Permanent(err)
	}
	req := Request{TenantID: env.TenantID, Notification: requested}
	for _, channel := range requested.Channels {
		outcome, err := d.deliverOnce(ctx, req, channel)
		if err != nil {
			return err
		}
		if err := d.publish(ctx, env.TenantID, outcome); err != nil {
			return err
		}
		d.log.Info("notification delivery finished", slog.String("eventId", env.EventID),
			slog.String("tenantId", env.TenantID), slog.String("notificationId", requested.NotificationID),
			slog.String("channel", string(channel)), slog.String("status", string(outcome.Status)))
	}
	return nil
}

// deliverOnce returns the cached outcome or delivers now. An error is
// returned only when ctx was cancelled (shutdown): nothing is published then.
func (d *Dispatcher) deliverOnce(ctx context.Context, req Request, channel notification.Channel) (notification.Delivered, error) {
	key := req.Notification.NotificationID + "/" + string(channel)
	if cached, ok := d.outcomes.Get(key); ok {
		return cached, nil
	}
	err := d.deliver(ctx, req, channel)
	if ctx.Err() != nil {
		return notification.Delivered{}, ctx.Err()
	}
	outcome := toOutcome(req.Notification.NotificationID, channel, err)
	d.outcomes.Put(key, outcome)
	return outcome, nil
}

func (d *Dispatcher) deliver(ctx context.Context, req Request, channel notification.Channel) error {
	if req.Notification.IsCounter() && channel != notification.ChannelWeb {
		return Skip(reasonCounterWebOnly)
	}
	sender, ok := d.senders[channel]
	if !ok {
		return Skip(reasonChannelNotEnabled)
	}
	return retry.Do(ctx, d.policy, func(ctx context.Context) error {
		err := sender.Send(ctx, req)
		if _, skipped := asSkip(err); skipped {
			return retry.Permanent(err) // a skip is final, never retried
		}
		return err
	})
}

func toOutcome(notificationID string, channel notification.Channel, err error) notification.Delivered {
	outcome := notification.Delivered{NotificationID: notificationID, Channel: channel, Status: notification.StatusSent}
	if skip, ok := asSkip(err); ok {
		outcome.Status = notification.StatusSkipped
		outcome.Error = skip.Reason
		return outcome
	}
	if err != nil {
		outcome.Status = notification.StatusFailed
		outcome.Error = textsafe.Sanitize(err.Error(), maxErrorRunes)
	}
	return outcome
}

func (d *Dispatcher) publish(ctx context.Context, tenantID string, outcome notification.Delivered) error {
	env, err := envelope.New(notification.TypeDelivered, tenantID, outcome, d.now())
	if err != nil {
		return err
	}
	return d.publisher.PublishEvent(ctx, d.deliveredTopic, env)
}
