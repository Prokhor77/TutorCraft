// Package notification is the notifier domain: the requested/delivered
// payloads, their validation, counters and link resolution. It has no I/O.
package notification

import (
	"encoding/json"
	"net/mail"
	"regexp"
	"strings"

	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/validate"
)

// Channel is a delivery channel.
type Channel string

const (
	ChannelEmail    Channel = "email"
	ChannelTelegram Channel = "telegram"
	ChannelWeb      Channel = "web"
)

// Status is a per-channel delivery outcome.
type Status string

const (
	StatusSent    Status = "sent"
	StatusFailed  Status = "failed"
	StatusSkipped Status = "skipped"
)

// Event types and limits.
const (
	TypeDelivered   = "notify.delivered"
	CategoryCounter = "counter"
	MaxTitleRunes   = 200
	MaxBodyRunes    = 4000
	maxLinkRunes    = 2048
	maxEmailRunes   = 254
	maxCategoryLen  = 64
	DefaultLocale   = "ru"
)

var (
	categoryPattern = regexp.MustCompile(`^[a-z][a-z0-9_]*$`)
	localePattern   = regexp.MustCompile(`^[a-z]{2}(-[A-Za-z]{2})?$`)
	allChannels     = []string{string(ChannelEmail), string(ChannelTelegram), string(ChannelWeb)}
)

// Requested is the payload of tc.notify.requested.v1.
type Requested struct {
	NotificationID string    `json:"notificationId"`
	UserID         string    `json:"userId"`
	Category       string    `json:"category"`
	Channels       []Channel `json:"channels"`
	Title          string    `json:"title"`
	Body           string    `json:"body"`
	Link           string    `json:"link"`
	Locale         string    `json:"locale"`
	Email          string    `json:"email"`
	TelegramChatID int64     `json:"telegramChatId"`
}

// IsCounter reports whether this is a real-time counter update (web only).
func (r Requested) IsCounter() bool {
	return r.Category == CategoryCounter
}

// Language returns the two-letter language of the locale.
func (r Requested) Language() string {
	if len(r.Locale) < 2 {
		return DefaultLocale
	}
	return r.Locale[:2]
}

// Decode parses and validates the payload.
func Decode(raw json.RawMessage) (Requested, error) {
	var r Requested
	if err := envelope.DecodePayload(raw, &r); err != nil {
		return Requested{}, err
	}
	var c validate.Collector
	c.UUID("notificationId", r.NotificationID)
	c.UUID("userId", r.UserID)
	c.Check(len(r.Category) <= maxCategoryLen && categoryPattern.MatchString(r.Category), "category", validate.ProblemInvalid)
	validateChannels(&c, r.Channels)
	if c.Required("title", r.Title) {
		c.MaxLen("title", r.Title, MaxTitleRunes)
	}
	c.MaxLen("body", r.Body, MaxBodyRunes)
	c.Check(r.Link == "" || isAllowedLink(r.Link), "link", validate.ProblemInvalid)
	c.Check(r.Locale == "" || localePattern.MatchString(r.Locale), "locale", validate.ProblemInvalid)
	c.Check(r.Email == "" || isEmail(r.Email), "email", validate.ProblemInvalid)
	if r.IsCounter() {
		_, err := ParseCounter(r.Body)
		c.Check(err == nil, "body", "must be a counter JSON object")
	}
	if err := c.Err(); err != nil {
		return Requested{}, err
	}
	return r, nil
}

func validateChannels(c *validate.Collector, channels []Channel) {
	if len(channels) == 0 {
		c.Add("channels", validate.ProblemRequired)
		return
	}
	seen := map[Channel]bool{}
	for _, ch := range channels {
		c.OneOf("channels", string(ch), allChannels...)
		c.Check(!seen[ch], "channels", "must not repeat")
		seen[ch] = true
	}
}

func isEmail(s string) bool {
	if len(s) > maxEmailRunes {
		return false
	}
	addr, err := mail.ParseAddress(s)
	return err == nil && addr.Address == s
}

// isAllowedLink accepts app-relative paths ("/courses/1") and absolute http(s) URLs.
func isAllowedLink(link string) bool {
	if len([]rune(link)) > maxLinkRunes || strings.ContainsAny(link, " \t\r\n\\") {
		return false
	}
	if strings.HasPrefix(link, "/") {
		return !strings.HasPrefix(link, "//")
	}
	return strings.HasPrefix(link, "https://") || strings.HasPrefix(link, "http://")
}

// Delivered is the payload of tc.notify.delivered.v1.
type Delivered struct {
	NotificationID string  `json:"notificationId"`
	Channel        Channel `json:"channel"`
	Status         Status  `json:"status"`
	Error          string  `json:"error,omitempty"`
}
