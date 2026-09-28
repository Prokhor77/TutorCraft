package email

import (
	"bytes"
	"fmt"
	"mime"
	"mime/multipart"
	"mime/quotedprintable"
	"net/mail"
	"net/textproto"
	"strings"
	"time"

	"github.com/tutorcraft/workerkit/ids"
	"github.com/tutorcraft/workerkit/textsafe"
)

const (
	crlf              = "\r\n"
	charsetUTF8       = "utf-8"
	contentTypePlain  = "text/plain; charset=utf-8"
	contentTypeHTML   = "text/html; charset=utf-8"
	encodingQP        = "quoted-printable"
	multipartAlt      = "multipart/alternative"
	mimeVersion       = "1.0"
	fallbackIDHost    = "tutorcraft.local"
	maxSubjectRunes   = 200
	headerContentType = "Content-Type"
	headerTransferEnc = "Content-Transfer-Encoding"
)

// Message is one email to one recipient.
type Message struct {
	From    *mail.Address
	To      string
	Subject string
	Text    string
	HTML    string
	Date    time.Time
}

// Bytes renders the message as RFC 5322 with a multipart/alternative body.
// Non-ASCII header text is encoded per RFC 2047; bodies are quoted-printable.
func (m Message) Bytes() ([]byte, error) {
	var body bytes.Buffer
	parts := multipart.NewWriter(&body)
	if err := writePart(parts, contentTypePlain, m.Text); err != nil {
		return nil, err
	}
	if err := writePart(parts, contentTypeHTML, m.HTML); err != nil {
		return nil, err
	}
	if err := parts.Close(); err != nil {
		return nil, fmt.Errorf("close multipart: %w", err)
	}
	var out bytes.Buffer
	for _, h := range m.headers(parts.Boundary()) {
		out.WriteString(h[0] + ": " + h[1] + crlf)
	}
	out.WriteString(crlf)
	out.Write(body.Bytes())
	return out.Bytes(), nil
}

func (m Message) headers(boundary string) [][2]string {
	subject := textsafe.Sanitize(m.Subject, maxSubjectRunes)
	return [][2]string{
		{"From", m.From.String()},
		{"To", (&mail.Address{Address: m.To}).String()},
		{"Subject", mime.BEncoding.Encode(charsetUTF8, subject)},
		{"Date", m.Date.Format(time.RFC1123Z)},
		{"Message-ID", "<" + ids.NewV7At(m.Date) + "@" + domainOf(m.From.Address) + ">"},
		{"MIME-Version", mimeVersion},
		{headerContentType, mime.FormatMediaType(multipartAlt, map[string]string{"boundary": boundary})},
	}
}

func writePart(parts *multipart.Writer, contentType, content string) error {
	header := textproto.MIMEHeader{}
	header.Set(headerContentType, contentType)
	header.Set(headerTransferEnc, encodingQP)
	w, err := parts.CreatePart(header)
	if err != nil {
		return fmt.Errorf("create %s part: %w", contentType, err)
	}
	qp := quotedprintable.NewWriter(w)
	if _, err := qp.Write([]byte(normalizeNewlines(content))); err != nil {
		return fmt.Errorf("write %s part: %w", contentType, err)
	}
	return qp.Close()
}

// normalizeNewlines converts bare LF to CRLF as SMTP requires.
func normalizeNewlines(s string) string {
	return strings.ReplaceAll(strings.ReplaceAll(s, crlf, "\n"), "\n", crlf)
}

func domainOf(address string) string {
	if at := strings.LastIndex(address, "@"); at >= 0 && at < len(address)-1 {
		return address[at+1:]
	}
	return fallbackIDHost
}
