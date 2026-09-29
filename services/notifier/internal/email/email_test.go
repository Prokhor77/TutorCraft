package email

import (
	"bytes"
	"context"
	"errors"
	"io"
	"mime"
	"mime/multipart"
	"mime/quotedprintable"
	"net"
	"net/mail"
	"net/textproto"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/retry"
)

const recipient = "student@example.com"

func testFrom() *mail.Address {
	return &mail.Address{Name: "TutorCraft", Address: "no-reply@tutorcraft.local"}
}

func testLinks() notification.LinkResolver {
	base, _ := url.Parse("http://localhost:3000")
	return notification.NewLinkResolver(base)
}

func testRequest() delivery.Request {
	return delivery.Request{Notification: notification.Requested{
		NotificationID: "0192f3c1-7b2a-7c3d-8e4f-123456789abc", Title: "Оценка <b>опубликована</b>",
		Body: "Строка 1\n<script>alert(1)</script>", Link: "/courses/1?tab=grades&x=1", Locale: "ru", Email: recipient,
	}}
}

type parsedEmail struct {
	header mail.Header
	parts  map[string]string // content type -> decoded body
}

func parseEmail(t *testing.T, raw []byte) parsedEmail {
	t.Helper()
	msg, err := mail.ReadMessage(bytes.NewReader(raw))
	if err != nil {
		t.Fatalf("read message: %v", err)
	}
	mediaType, params, err := mime.ParseMediaType(msg.Header.Get("Content-Type"))
	if err != nil || mediaType != "multipart/alternative" {
		t.Fatalf("content type %q: %v", mediaType, err)
	}
	parsed := parsedEmail{header: msg.Header, parts: map[string]string{}}
	reader := multipart.NewReader(msg.Body, params["boundary"])
	for {
		part, err := reader.NextRawPart()
		if errors.Is(err, io.EOF) {
			return parsed
		}
		if err != nil {
			t.Fatal(err)
		}
		if part.Header.Get("Content-Transfer-Encoding") != "quoted-printable" {
			t.Fatal("parts must be quoted-printable")
		}
		body, err := io.ReadAll(quotedprintable.NewReader(part))
		if err != nil {
			t.Fatal(err)
		}
		parsed.parts[part.Header.Get("Content-Type")] = string(body)
	}
}

func composeTestEmail(t *testing.T) parsedEmail {
	t.Helper()
	sender := NewSender(nil, testFrom(), testLinks())
	sender.now = func() time.Time { return time.Date(2026, 9, 27, 18, 0, 0, 0, time.UTC) }
	raw, err := sender.compose(testRequest().Notification)
	if err != nil {
		t.Fatal(err)
	}
	for _, line := range strings.Split(strings.SplitN(string(raw), "\r\n\r\n", 2)[0], "\r\n") {
		for _, r := range line {
			if r > 127 {
				t.Fatalf("raw non-ASCII in header line %q", line)
			}
		}
	}
	return parseEmail(t, raw)
}

func TestMessageHeadersAndCyrillicSubject(t *testing.T) {
	parsed := composeTestEmail(t)
	encoded := parsed.header.Get("Subject")
	if !strings.HasPrefix(encoded, "=?utf-8?b?") {
		t.Fatalf("subject not RFC 2047 encoded: %q", encoded)
	}
	decoded, err := new(mime.WordDecoder).DecodeHeader(encoded)
	if err != nil || decoded != "Оценка <b>опубликована</b>" {
		t.Fatalf("decoded subject %q, %v", decoded, err)
	}
	if parsed.header.Get("To") != "<"+recipient+">" || parsed.header.Get("MIME-Version") != "1.0" {
		t.Fatalf("headers %v", parsed.header)
	}
	if from, err := mail.ParseAddress(parsed.header.Get("From")); err != nil || from.Address != testFrom().Address {
		t.Fatalf("from %q", parsed.header.Get("From"))
	}
	if !strings.HasSuffix(parsed.header.Get("Message-Id"), "@tutorcraft.local>") || parsed.header.Get("Date") == "" {
		t.Fatalf("message-id/date %v", parsed.header)
	}
}

func TestMessageBodiesAreEscapedAndLinkIsAbsolute(t *testing.T) {
	parsed := composeTestEmail(t)
	html := parsed.parts["text/html; charset=utf-8"]
	text := parsed.parts["text/plain; charset=utf-8"]
	if strings.Contains(html, "<script>") || strings.Contains(html, "<b>опубликована") {
		t.Fatalf("HTML not escaped: %s", html)
	}
	if !strings.Contains(html, "&lt;script&gt;") || !strings.Contains(html, `href="http://localhost:3000/courses/1?tab=grades&amp;x=1"`) {
		t.Fatalf("unexpected HTML: %s", html)
	}
	if !strings.Contains(html, ">Открыть</a>") {
		t.Fatalf("missing localized button: %s", html)
	}
	if !strings.Contains(text, "Открыть: http://localhost:3000/courses/1?tab=grades&x=1\r\n") ||
		!strings.Contains(text, "Строка 1\r\n") {
		t.Fatalf("unexpected text part: %q", text)
	}
}

func TestAccountEmailUsesActionLabelAndServiceFooter(t *testing.T) {
	n := testRequest().Notification
	n.Category = categoryAccount
	n.ActionLabel = "Задать пароль"
	n.Body = "Вас пригласили на курс.\nСсылка действует 7 дн."
	text, html, err := render(newContent(n, "http://localhost:3000/accept-invite?token=abc"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(html, ">Задать пароль</a>") || strings.Contains(html, ">Открыть</a>") {
		t.Fatalf("action label not used: %s", html)
	}
	if !strings.Contains(html, labelsFor("ru").AccountFooter) || strings.Contains(html, "включены уведомления") {
		t.Fatalf("account footer expected: %s", html)
	}
	if !strings.Contains(html, `mso-hide:all;">Вас пригласили на курс.</div>`) {
		t.Fatalf("preheader expected: %s", html)
	}
	if !strings.Contains(text, "Задать пароль: http://localhost:3000/accept-invite?token=abc\n") {
		t.Fatalf("plain-text link expected: %q", text)
	}
}

func TestSenderSkipsWithoutAddressOrTransport(t *testing.T) {
	req := testRequest()
	if err := NewSender(nil, testFrom(), testLinks()).Send(context.Background(), req); !isSkip(err) {
		t.Fatalf("disabled transport: want skip, got %v", err)
	}
	req.Notification.Email = ""
	if err := NewSender(&fakeTransport{}, testFrom(), testLinks()).Send(context.Background(), req); !isSkip(err) {
		t.Fatalf("no address: want skip, got %v", err)
	}
}

func isSkip(err error) bool {
	var skip *delivery.SkipError
	return errors.As(err, &skip)
}

type fakeTransport struct {
	err  error
	from string
	to   []string
}

func (f *fakeTransport) Send(_ context.Context, from string, to []string, _ []byte) error {
	f.from, f.to = from, to
	return f.err
}

func TestSenderClassifiesSMTPReplies(t *testing.T) {
	permanent := &fakeTransport{err: &textproto.Error{Code: 550, Msg: "user " + recipient + " unknown"}}
	err := NewSender(permanent, testFrom(), testLinks()).Send(context.Background(), testRequest())
	if !retry.IsPermanent(err) || strings.Contains(err.Error(), recipient) {
		t.Fatalf("want permanent error without address, got %v", err)
	}
	if permanent.from != testFrom().Address || permanent.to[0] != recipient {
		t.Fatalf("envelope from=%s to=%v", permanent.from, permanent.to)
	}
	transient := &fakeTransport{err: &textproto.Error{Code: 451, Msg: "try later"}}
	err = NewSender(transient, testFrom(), testLinks()).Send(context.Background(), testRequest())
	if err == nil || retry.IsPermanent(err) {
		t.Fatalf("want transient error, got %v", err)
	}
}

// fakeSMTPServer speaks just enough SMTP (no STARTTLS, like Mailpit) to accept one message.
func fakeSMTPServer(t *testing.T, rcptReply string) (addr string, received <-chan string) {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	data := make(chan string, 1)
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		serveSMTP(textproto.NewConn(conn), rcptReply, data)
	}()
	return listener.Addr().String(), data
}

func serveSMTP(c *textproto.Conn, rcptReply string, data chan<- string) {
	_ = c.PrintfLine("220 fake ESMTP")
	for {
		line, err := c.ReadLine()
		if err != nil {
			return
		}
		switch verb := strings.ToUpper(strings.Fields(line + " x")[0]); verb {
		case "EHLO", "HELO":
			_ = c.PrintfLine("250-fake\r\n250 8BITMIME")
		case "RCPT":
			_ = c.PrintfLine("%s", rcptReply)
		case "DATA":
			_ = c.PrintfLine("354 go ahead")
			body, _ := io.ReadAll(c.DotReader())
			data <- string(body)
			_ = c.PrintfLine("250 queued")
		case "QUIT":
			_ = c.PrintfLine("221 bye")
			return
		default:
			_ = c.PrintfLine("250 ok")
		}
	}
}

func transportFor(t *testing.T, addr string) SMTPTransport {
	t.Helper()
	host, portText, _ := net.SplitHostPort(addr)
	port, _ := strconv.Atoi(portText)
	return SMTPTransport{Host: host, Port: port, Timeout: 5 * time.Second}
}

func TestSMTPTransportDeliversOverPlainConnection(t *testing.T) {
	addr, received := fakeSMTPServer(t, "250 ok")
	err := transportFor(t, addr).Send(context.Background(), "a@b.c", []string{recipient}, []byte("Subject: x\r\n\r\nhello\r\n"))
	if err != nil {
		t.Fatal(err)
	}
	select {
	case body := <-received:
		if !strings.Contains(body, "hello") {
			t.Fatalf("body %q", body)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("no message received")
	}
}

func TestSMTPTransportReportsRejectedRecipient(t *testing.T) {
	addr, _ := fakeSMTPServer(t, "550 no such user "+recipient)
	err := transportFor(t, addr).Send(context.Background(), "a@b.c", []string{recipient}, []byte("x"))
	reply, ok := toReplyError(err)
	if !ok || reply.Code != 550 || !reply.IsPermanent() {
		t.Fatalf("want 550 reply error, got %v", err)
	}
}

func TestSMTPTransportRefusesAuthWithoutSupport(t *testing.T) {
	addr, _ := fakeSMTPServer(t, "250 ok")
	transport := transportFor(t, addr)
	transport.Username, transport.Password = "user", "secret"
	err := transport.Send(context.Background(), "a@b.c", []string{recipient}, []byte("x"))
	if !errors.Is(err, ErrAuthUnsupported) {
		t.Fatalf("want ErrAuthUnsupported, got %v", err)
	}
}

func TestParagraphsAndLabelsFallback(t *testing.T) {
	if got := paragraphs("a\n\n b \n"); len(got) != 2 || got[1] != "b" {
		t.Fatalf("paragraphs %v", got)
	}
	if labelsFor("de").Open != "Открыть" || labelsFor("en").Open != "Open" {
		t.Fatal("labels fallback")
	}
}
