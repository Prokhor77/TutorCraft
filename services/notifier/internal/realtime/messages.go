package realtime

import (
	"encoding/json"

	"github.com/tutorcraft/notifier/internal/notification"
)

// Server → client message types (docs/events/README.md, "WebSocket").
const (
	MessageTypeNotification = "notification"
	MessageTypeCounter      = "counter"
)

type outbound struct {
	Type string `json:"type"`
	Data any    `json:"data"`
}

type notificationData struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	Body     string `json:"body"`
	Link     string `json:"link,omitempty"`
	Category string `json:"category"`
}

// EncodeMessage renders the WebSocket message for a notification: a counter
// update for category "counter", otherwise a notification.
func EncodeMessage(n notification.Requested) ([]byte, error) {
	if n.IsCounter() {
		counter, err := notification.ParseCounter(n.Body)
		if err != nil {
			return nil, err
		}
		return json.Marshal(outbound{Type: MessageTypeCounter, Data: counter})
	}
	return json.Marshal(outbound{Type: MessageTypeNotification, Data: notificationData{
		ID: n.NotificationID, Title: n.Title, Body: n.Body, Link: n.Link, Category: n.Category,
	}})
}
