package telegram

import (
	"context"
	"errors"

	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/retry"
)

const (
	reasonNotConfigured = "telegram bot is not configured"
	reasonNoChat        = "recipient has no linked telegram chat"
)

// MessageAPI sends messages.
type MessageAPI interface {
	SendMessage(ctx context.Context, req SendMessageRequest) error
}

// Sender is the Telegram delivery channel.
type Sender struct {
	api   MessageAPI // nil = bot not configured
	links notification.LinkResolver
}

// NewSender creates the channel; pass a nil api when no bot token is configured.
func NewSender(api MessageAPI, links notification.LinkResolver) *Sender {
	return &Sender{api: api, links: links}
}

// Channel implements delivery.Sender.
func (s *Sender) Channel() notification.Channel { return notification.ChannelTelegram }

// Send implements delivery.Sender.
func (s *Sender) Send(ctx context.Context, req delivery.Request) error {
	if s.api == nil {
		return delivery.Skip(reasonNotConfigured)
	}
	n := req.Notification
	if n.TelegramChatID == 0 {
		return delivery.Skip(reasonNoChat)
	}
	return classify(s.api.SendMessage(ctx, FormatNotification(n.TelegramChatID, n, s.links.Absolute(n.Link))))
}

// classify: 429 → retry after the server-given delay; 5xx and network errors
// → retry; other API errors (400 bad chat, 403 bot blocked) → permanent.
func classify(err error) error {
	var apiErr *APIError
	if err == nil || !errors.As(err, &apiErr) {
		return err
	}
	switch {
	case apiErr.Code == codeTooManyRequests:
		return retry.After(apiErr.RetryAfter, err)
	case apiErr.Code >= firstServerErrorCode:
		return err
	default:
		return retry.Permanent(err)
	}
}
