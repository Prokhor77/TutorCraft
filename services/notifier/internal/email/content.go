// Package email renders notification emails (multipart/alternative, plain
// text + HTML) and delivers them over SMTP.
package email

import (
	htmltemplate "html/template"
	"strings"
	texttemplate "text/template"

	"github.com/tutorcraft/notifier/internal/notification"
)

// labels are the localized fixed strings of the email.
type labels struct {
	Open   string
	Footer string
}

var labelsByLanguage = map[string]labels{
	"ru": {
		Open:   "Открыть",
		Footer: "Вы получили это письмо, потому что у вас включены уведомления TutorCraft. Изменить настройки можно в профиле.",
	},
	"en": {
		Open:   "Open",
		Footer: "You received this email because TutorCraft notifications are enabled. You can change this in your profile.",
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
	Body       string
	Paragraphs []string
	Link       string
	Labels     labels
}

// html/template escapes every value and rejects unsafe URLs in href.
var htmlTemplate = htmltemplate.Must(htmltemplate.New("html").Parse(`<!DOCTYPE html>
<html lang="{{.Lang}}">
<head><meta charset="utf-8"><title>{{.Title}}</title></head>
<body style="margin:0;padding:24px;font-family:Arial,Helvetica,sans-serif;color:#1f2933;background:#ffffff;">
<h1 style="margin:0 0 16px;font-size:20px;">{{.Title}}</h1>
{{range .Paragraphs}}<p style="margin:0 0 12px;font-size:15px;line-height:1.5;">{{.}}</p>
{{end}}{{if .Link}}<p style="margin:24px 0;"><a href="{{.Link}}" style="display:inline-block;padding:10px 20px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:6px;">{{.Labels.Open}}</a></p>
{{end}}<hr style="border:none;border-top:1px solid #e4e7eb;margin:24px 0;">
<p style="margin:0;font-size:12px;color:#7b8794;">{{.Labels.Footer}}</p>
</body>
</html>
`))

var textTemplate = texttemplate.Must(texttemplate.New("text").Parse(`{{.Title}}

{{if .Body}}{{.Body}}

{{end}}{{if .Link}}{{.Labels.Open}}: {{.Link}}

{{end}}--
{{.Labels.Footer}}
`))

func newContent(n notification.Requested, absoluteLink string) content {
	return content{
		Lang:       n.Language(),
		Title:      n.Title,
		Body:       n.Body,
		Paragraphs: paragraphs(n.Body),
		Link:       absoluteLink,
		Labels:     labelsFor(n.Language()),
	}
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
	if err := textTemplate.Execute(&textOut, c); err != nil {
		return "", "", err
	}
	if err := htmlTemplate.Execute(&htmlOut, c); err != nil {
		return "", "", err
	}
	return textOut.String(), htmlOut.String(), nil
}
