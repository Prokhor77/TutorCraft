// Package email renders notification emails (multipart/alternative, plain
// text + HTML) and delivers them over SMTP.
package email

import (
	"embed"
	htmltemplate "html/template"
	"strings"
	texttemplate "text/template"

	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/textsafe"
)

const (
	htmlTemplateFile = "templates/notification.html"
	textTemplateFile = "templates/notification.txt"
	// categoryAccount marks service emails (invitation, password reset) that are sent regardless of preferences.
	categoryAccount = "account"
	maxPreheader    = 140
)

// labels are the localized fixed strings of the email.
type labels struct {
	Brand         string
	Open          string
	LinkHint      string
	Footer        string
	AccountFooter string
}

var labelsByLanguage = map[string]labels{
	"ru": {
		Brand:         "TutorCraft",
		Open:          "Открыть",
		LinkHint:      "Если кнопка не открывается, скопируйте ссылку в адресную строку браузера:",
		Footer:        "Вы получили это письмо, потому что у вас включены уведомления TutorCraft. Изменить настройки можно в профиле.",
		AccountFooter: "Это служебное письмо TutorCraft. Если вы его не ждали, просто проигнорируйте — без перехода по ссылке ничего не произойдёт.",
	},
	"en": {
		Brand:         "TutorCraft",
		Open:          "Open",
		LinkHint:      "If the button does not work, copy this link into your browser's address bar:",
		Footer:        "You received this email because TutorCraft notifications are enabled. You can change this in your profile.",
		AccountFooter: "This is a TutorCraft service email. If you were not expecting it, just ignore it — nothing happens unless you follow the link.",
	},
}

func labelsFor(language string) labels {
	if l, ok := labelsByLanguage[language]; ok {
		return l
	}
	return labelsByLanguage[notification.DefaultLocale]
}

// content is the data passed to both templates.
type content struct {
	Lang       string
	Title      string
	Preheader  string
	Paragraphs []string
	Link       string
	Action     string
	Footer     string
	Labels     labels
}

//go:embed templates
var templateFiles embed.FS

// html/template escapes every value and rejects unsafe URLs in href.
var (
	htmlTemplate = htmltemplate.Must(htmltemplate.ParseFS(templateFiles, htmlTemplateFile))
	textTemplate = texttemplate.Must(texttemplate.ParseFS(templateFiles, textTemplateFile))
)

func newContent(n notification.Requested, absoluteLink string) content {
	l := labelsFor(n.Language())
	body := paragraphs(n.Body)
	return content{
		Lang:       n.Language(),
		Title:      n.Title,
		Preheader:  preheader(body),
		Paragraphs: body,
		Link:       absoluteLink,
		Action:     actionLabel(n, l),
		Footer:     footer(n, l),
		Labels:     l,
	}
}

func actionLabel(n notification.Requested, l labels) string {
	if label := strings.TrimSpace(n.ActionLabel); label != "" {
		return label
	}
	return l.Open
}

func footer(n notification.Requested, l labels) string {
	if n.Category == categoryAccount {
		return l.AccountFooter
	}
	return l.Footer
}

// preheader is the inbox preview line shown next to the subject.
func preheader(body []string) string {
	if len(body) == 0 {
		return ""
	}
	return textsafe.Truncate(body[0], maxPreheader)
}

func paragraphs(body string) []string {
	var out []string
	for _, line := range strings.Split(body, "\n") {
		if trimmed := strings.TrimSpace(line); trimmed != "" {
			out = append(out, trimmed)
		}
	}
	return out
}

func render(c content) (text string, html string, err error) {
	var textOut, htmlOut strings.Builder
	if err := textTemplate.ExecuteTemplate(&textOut, "notification.txt", c); err != nil {
		return "", "", err
	}
	if err := htmlTemplate.ExecuteTemplate(&htmlOut, "notification.html", c); err != nil {
		return "", "", err
	}
	return textOut.String(), htmlOut.String(), nil
}
