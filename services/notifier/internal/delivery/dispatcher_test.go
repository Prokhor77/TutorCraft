package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"maps"
	"testing"
	"time"

	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/logging"
	"github.com/tutorcraft/workerkit/retry"
	"github.com/tutorcraft/workerkit/ttlcache"
)

const (
	tenantID       = "0192f3c1-0000-7000-8000-000000000001"
	notificationID = "0192f3c1-7b2a-7c3d-8e4f-123456789abc"
	userID         = "0192f3c1-0000-7000-8000-000000000002"
	deliveredTopic = "tc.notify.delivered.v1"
)

type fakeSender struct {
	channel notification.Channel
	errs    []error // returned in order; nil afterwards
	calls   int
	last    Request
}

func (f *fakeSender) Channel() notification.Channel { return f.channel }

func (f *fakeSender) Send(_ context.Context, req Request) error {
	f.calls++
	f.last = req
	if f.calls <= len(f.errs) {
		return f.errs[f.calls-1]
	}
	return nil
}

type fakePublisher struct {
	delivered []notification.Delivered
	err       error
}

func (f *fakePublisher) PublishEvent(_ context.Context, topic string, env envelope.Envelope) error {
	if f.err != nil {
		return f.err
	}
	if topic != deliveredTopic || env.TenantID != tenantID || env.Type != notification.TypeDelivered {
		return errors.New("unexpected envelope")
	}
	var d notification.Delivered
	if err := json.Unmarshal(env.Payload, &d); err != nil {
		return err
	}
	f.delivered = append(f.delivered, d)
	return nil
}

type fixture struct {
	email, telegram, web *fakeSender
	publisher            *fakePublisher
	dispatcher           *Dispatcher
}

func newFixture() *fixture {
	f := &fixture{
		email:     &fakeSender{channel: notification.ChannelEmail},
		telegram:  &fakeSender{channel: notification.ChannelTelegram},
		web:       &fakeSender{channel: notification.ChannelWeb},
		publisher: &fakePublisher{},
	}
	f.dispatcher = NewDispatcher(Options{
		Senders: []Sender{f.email, f.telegram, f.web}, Publisher: f.publisher, DeliveredTopic: deliveredTopic,
		Outcomes: ttlcache.New[notification.Delivered](100, time.Hour),
		Retry:    retry.Policy{Attempts: 3, InitialDelay: time.Millisecond, MaxDelay: 10 * time.Millisecond, Multiplier: 2},
		Log:      logging.Discard(),
	})
	return f
}

func requestEvent(t *testing.T, mutate func(map[string]any)) envelope.Envelope {
	t.Helper()
	payload := map[string]any{
		"notificationId": notificationID, "userId": userID, "category": "grade_published",
		"channels": []string{"email", "telegram", "web"}, "title": "t", "body": "b", "link": "/x", "locale": "ru",
	}
	if mutate != nil {
		mutate(payload)
	}
	env, err := envelope.New("notify.requested", tenantID, payload, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	return env
}

func statuses(ds []notification.Delivered) map[notification.Channel]notification.Status {
	out := map[notification.Channel]notification.Status{}
	for _, d := range ds {
		out[d.Channel] = d.Status
	}
	return out
}

func TestHandleRoutesEachChannelAndPublishesOutcome(t *testing.T) {
	f := newFixture()
	f.email.errs = []error{Skip("no email address")}
	f.telegram.errs = []error{errors.New("timeout"), errors.New("timeout"), errors.New("timeout")}
	if err := f.dispatcher.Handle(context.Background(), requestEvent(t, nil)); err != nil {
		t.Fatal(err)
	}
	got := statuses(f.publisher.delivered)
	want := map[notification.Channel]notification.Status{
		notification.ChannelEmail: notification.StatusSkipped, notification.ChannelTelegram: notification.StatusFailed,
		notification.ChannelWeb: notification.StatusSent,
	}
	if !maps.Equal(got, want) {
		t.Fatalf("statuses %v, want %v", got, want)
	}
	if f.email.calls != 1 || f.telegram.calls != 3 || f.web.calls != 1 {
		t.Fatalf("calls email=%d telegram=%d web=%d", f.email.calls, f.telegram.calls, f.web.calls)
	}
	if f.web.last.TenantID != tenantID || f.web.last.Notification.UserID != userID {
		t.Fatalf("request not passed through: %+v", f.web.last)
	}
}

func TestHandlePermanentErrorIsNotRetried(t *testing.T) {
	f := newFixture()
	f.telegram.errs = []error{retry.Permanent(errors.New("403 blocked"))}
	if err := f.dispatcher.Handle(context.Background(), requestEvent(t, nil)); err != nil {
		t.Fatal(err)
	}
	if f.telegram.calls != 1 || statuses(f.publisher.delivered)[notification.ChannelTelegram] != notification.StatusFailed {
		t.Fatalf("calls=%d delivered=%v", f.telegram.calls, f.publisher.delivered)
	}
}

func TestHandleCounterGoesToWebOnly(t *testing.T) {
	f := newFixture()
	event := requestEvent(t, func(m map[string]any) {
		m["category"] = notification.CategoryCounter
		m["body"] = `{"name":"grading_queue","value":3}`
	})
	if err := f.dispatcher.Handle(context.Background(), event); err != nil {
		t.Fatal(err)
	}
	if f.email.calls != 0 || f.telegram.calls != 0 || f.web.calls != 1 {
		t.Fatalf("counter routed wrongly: email=%d telegram=%d web=%d", f.email.calls, f.telegram.calls, f.web.calls)
	}
	if statuses(f.publisher.delivered)[notification.ChannelEmail] != notification.StatusSkipped {
		t.Fatal("email must be reported skipped")
	}
}

func TestHandleIsIdempotentPerNotificationAndChannel(t *testing.T) {
	f := newFixture()
	f.publisher.err = errors.New("broker down")
	if err := f.dispatcher.Handle(context.Background(), requestEvent(t, nil)); err == nil {
		t.Fatal("publish failure must be returned for retry")
	}
	f.publisher.err = nil
	// Redelivery (new eventId, same notificationId) must not resend.
	if err := f.dispatcher.Handle(context.Background(), requestEvent(t, nil)); err != nil {
		t.Fatal(err)
	}
	if f.email.calls != 1 || f.telegram.calls != 1 || f.web.calls != 1 {
		t.Fatalf("resent: email=%d telegram=%d web=%d", f.email.calls, f.telegram.calls, f.web.calls)
	}
	if len(f.publisher.delivered) != 3 {
		t.Fatalf("want 3 delivered events, got %d", len(f.publisher.delivered))
	}
}

func TestHandleInvalidPayloadIsPermanent(t *testing.T) {
	f := newFixture()
	err := f.dispatcher.Handle(context.Background(), requestEvent(t, func(m map[string]any) { m["userId"] = "x" }))
	if !retry.IsPermanent(err) {
		t.Fatalf("want permanent error, got %v", err)
	}
}

func TestHandleCancelledContextPublishesNothing(t *testing.T) {
	f := newFixture()
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	f.email.errs = []error{context.Canceled}
	if err := f.dispatcher.Handle(ctx, requestEvent(t, nil)); err == nil {
		t.Fatal("expected cancellation error")
	}
	if len(f.publisher.delivered) != 0 {
		t.Fatal("nothing must be published on shutdown")
	}
}
