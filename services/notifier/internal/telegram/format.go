package telegram

import (
	"strings"

	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/textsafe"
)

const (
	ParseModeHTML = "HTML"
	// Telegram allows 4096 characters per message; title (<=200) + body fit.
	maxTitleRunes = notification.MaxTitleRunes
	maxBodyRunes  = 3800
)

var openButtonLabels = map[string]string{"ru": "Открыть", "en": "Open", "uz": "Ochish"}

// htmlEscaper escapes exactly what Telegram's HTML parse mode requires.
var htmlEscaper = strings.NewReplacer("&", "&amp;", "<", "&lt;", ">", "&gt;", `"`, "&quot;")

// EscapeHTML makes user text safe for parse_mode=HTML.
func EscapeHTML(s string) string {
	return htmlEscaper.Replace(s)
}

// FormatNotification builds the sendMessage request: bold title, body and an
// «Открыть» button when the notification has a link. Text is truncated
// before escaping so that entities are never cut in half.
func FormatNotification(chatID int64, n notification.Requested, absoluteLink string) SendMessageRequest {
	text := "<b>" + EscapeHTML(textsafe.Truncate(n.Title, maxTitleRunes)) + "</b>"
	if body := strings.TrimSpace(n.Body); body != "" {
		text += "\n\n" + EscapeHTML(textsafe.Truncate(body, maxBodyRunes))
	}
	req := SendMessageRequest{
		ChatID:             chatID,
		Text:               text,
		ParseMode:          ParseModeHTML,
		LinkPreviewOptions: &LinkPreviewOptions{IsDisabled: true},
	}
	if absoluteLink != "" {
		button := InlineKeyboardButton{Text: buttonLabel(n), URL: absoluteLink}
		req.ReplyMarkup = &InlineKeyboardMarkup{InlineKeyboard: [][]InlineKeyboardButton{{button}}}
	}
	return req
}

// buttonLabel prefers the producer's action label over the generic «Открыть».
func buttonLabel(n notification.Requested) string {
	if label := strings.TrimSpace(n.ActionLabel); label != "" {
		return label
	}
	return openButtonLabel(n.Language())
}

func openButtonLabel(language string) string {
	if label, ok := openButtonLabels[language]; ok {
		return label
	}
	return openButtonLabels[notification.DefaultLocale]
}
