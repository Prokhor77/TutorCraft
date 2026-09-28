package realtime

import (
	"context"

	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/retry"
)

const reasonOffline = "recipient has no open websocket connection"

// Publisher fans a message out to a user's connections.
type Publisher interface {
	Publish(userID, tenantID string, msg []byte) int
}

// Sender is the "web" delivery channel. Web pushes are best effort: the
// in-app notification is already stored by core-api, so an offline user is
// "skipped", not "failed".
type Sender struct {
	hub Publisher
}

// NewSender creates the web channel.
func NewSender(hub Publisher) *Sender {
	return &Sender{hub: hub}
}

// Channel implements delivery.Sender.
func (s *Sender) Channel() notification.Channel { return notification.ChannelWeb }

// Send implements delivery.Sender.
func (s *Sender) Send(_ context.Context, req delivery.Request) error {
	msg, err := EncodeMessage(req.Notification)
	if err != nil {
		return retry.Permanent(err)
	}
	if s.hub.Publish(req.Notification.UserID, req.TenantID, msg) == 0 {
		return delivery.Skip(reasonOffline)
	}
	return nil
}
