package email

import (
	"context"
	"net/mail"
	"time"

	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/retry"
)

const (
	reasonNotConfigured = "email is not configured"
	reasonNoAddress     = "recipient has no email address"
)

// Transport sends a rendered message.
type Transport interface {
	Send(ctx context.Context, from string, to []string, msg []byte) error
}

// Sender is the email delivery channel.
type Sender struct {
	transport Transport // nil = channel disabled
	from      *mail.Address
	links     notification.LinkResolver
	now       func() time.Time
}

// NewSender creates the email channel; pass a nil transport to disable it.
func NewSender(transport Transport, from *mail.Address, links notification.LinkResolver) *Sender {
	return &Sender{transport: transport, from: from, links: links, now: time.Now}
}

// Channel implements delivery.Sender.
func (s *Sender) Channel() notification.Channel { return notification.ChannelEmail }

// Send implements delivery.Sender.
func (s *Sender) Send(ctx context.Context, req delivery.Request) error {
	if s.transport == nil {
		return delivery.Skip(reasonNotConfigured)
	}
	n := req.Notification
	if n.Email == "" {
		return delivery.Skip(reasonNoAddress)
	}
	raw, err := s.compose(n)
	if err != nil {
		return retry.Permanent(err)
	}
	return classify(s.transport.Send(ctx, s.from.Address, []string{n.Email}, raw))
}

func (s *Sender) compose(n notification.Requested) ([]byte, error) {
	text, html, err := render(newContent(n, s.links.Absolute(n.Link)))
	if err != nil {
		return nil, err
	}
	msg := Message{From: s.from, To: n.Email, Subject: n.Title, Text: text, HTML: html, Date: s.now()}
	return msg.Bytes()
}

// classify hides server reply texts and marks 5xx replies as permanent.
func classify(err error) error {
	if err == nil {
		return nil
	}
	reply, ok := toReplyError(err)
	if !ok {
		return err
	}
	if reply.IsPermanent() {
		return retry.Permanent(reply)
	}
	return reply
}
