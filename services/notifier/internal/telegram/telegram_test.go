package telegram

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/envelope"
	"github.com/tutorcraft/workerkit/logging"
	"github.com/tutorcraft/workerkit/retry"
)

const (
	testToken  = "123456:SECRET-TOKEN"
	testChatID = int64(987654321)
	linkCode   = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"
)

func testLinks() notification.LinkResolver {
	base, _ := url.Parse("https://app.tutorcraft.ru")
	return notification.NewLinkResolver(base)
}

func TestEscapeHTML(t *testing.T) {
	got := EscapeHTML(`<b>Tom & "Jerry"</b>`)
	if got != "&lt;b&gt;Tom &amp; &quot;Jerry&quot;&lt;/b&gt;" {
		t.Fatalf("escaped %q", got)
	}
}

func TestFormatNotification(t *testing.T) {
	n := notification.Requested{Title: "Оценка <5>", Body: "a & b", Link: "/courses/1", Locale: "ru"}
	req := FormatNotification(testChatID, n, testLinks().Absolute(n.Link))
	if req.Text != "<b>Оценка &lt;5&gt;</b>\n\na &amp; b" || req.ParseMode != ParseModeHTML || req.ChatID != testChatID {
		t.Fatalf("unexpected request %+v", req)
	}
	button := req.ReplyMarkup.InlineKeyboard[0][0]
	if button.Text != "Открыть" || button.URL != "https://app.tutorcraft.ru/courses/1" {
		t.Fatalf("unexpected button %+v", button)
	}
	if noLink := FormatNotification(testChatID, notification.Requested{Title: "t"}, ""); noLink.ReplyMarkup != nil {
		t.Fatal("no button expected without link")
	}
}

func TestFormatNotificationTruncatesBeforeEscaping(t *testing.T) {
	n := notification.Requested{Title: "t", Body: strings.Repeat("<", maxBodyRunes+10)}
	text := FormatNotification(testChatID, n, "").Text
	if strings.HasSuffix(text, "&lt") || !strings.HasSuffix(text, "…") {
		t.Fatalf("entity cut or not truncated: ...%s", text[len(text)-10:])
	}
}

// botServer is a fake Bot API. Each call pops the next scripted response.
type botServer struct {
	mu        sync.Mutex
	responses []string
	statuses  []int
	requests  []map[string]any
	paths     []string
}

func (b *botServer) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	b.mu.Lock()
	defer b.mu.Unlock()
	body, _ := io.ReadAll(r.Body)
	var decoded map[string]any
	_ = json.Unmarshal(body, &decoded)
	b.requests = append(b.requests, decoded)
	b.paths = append(b.paths, r.URL.Path)
	status, response := http.StatusOK, `{"ok":true,"result":true}`
	if len(b.responses) > 0 {
		status, response = b.statuses[0], b.responses[0]
		b.statuses, b.responses = b.statuses[1:], b.responses[1:]
	}
	w.WriteHeader(status)
	_, _ = io.WriteString(w, response)
}

func (b *botServer) script(status int, response string) *botServer {
	b.statuses = append(b.statuses, status)
	b.responses = append(b.responses, response)
	return b
}

func newBot(t *testing.T, b *botServer) *Client {
	t.Helper()
	server := httptest.NewServer(b)
	t.Cleanup(server.Close)
	return NewClient(server.URL, testToken)
}

func request(chatID int64) delivery.Request {
	return delivery.Request{Notification: notification.Requested{Title: "t", Body: "b", Link: "/x", TelegramChatID: chatID}}
}

func TestSenderRetriesAfter429(t *testing.T) {
	bot := (&botServer{}).script(http.StatusTooManyRequests,
		`{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 1","parameters":{"retry_after":1}}`)
	sender := NewSender(newBot(t, bot), testLinks())
	policy := retry.Policy{Attempts: 3, InitialDelay: time.Millisecond, MaxDelay: 5 * time.Second, Multiplier: 2}
	start := time.Now()
	err := retry.Do(context.Background(), policy, func(ctx context.Context) error { return sender.Send(ctx, request(testChatID)) })
	if err != nil {
		t.Fatalf("expected success after retry, got %v", err)
	}
	if elapsed := time.Since(start); elapsed < time.Second {
		t.Fatalf("retry_after not honoured: %v", elapsed)
	}
	if len(bot.requests) != 2 || bot.paths[0] != "/bot"+testToken+"/sendMessage" {
		t.Fatalf("requests=%d paths=%v", len(bot.requests), bot.paths)
	}
	if bot.requests[1]["parse_mode"] != "HTML" || bot.requests[1]["chat_id"] != float64(testChatID) {
		t.Fatalf("unexpected body %v", bot.requests[1])
	}
}

func TestSenderClassifiesErrors(t *testing.T) {
	blocked := (&botServer{}).script(http.StatusForbidden, `{"ok":false,"error_code":403,"description":"Forbidden: bot was blocked by the user"}`)
	err := NewSender(newBot(t, blocked), testLinks()).Send(context.Background(), request(testChatID))
	if !retry.IsPermanent(err) || !strings.Contains(err.Error(), "blocked") {
		t.Fatalf("403 must be permanent, got %v", err)
	}
	down := (&botServer{}).script(http.StatusBadGateway, `{"ok":false,"error_code":502,"description":"Bad Gateway"}`)
	err = NewSender(newBot(t, down), testLinks()).Send(context.Background(), request(testChatID))
	if err == nil || retry.IsPermanent(err) {
		t.Fatalf("502 must be transient, got %v", err)
	}
}

func TestSenderSkips(t *testing.T) {
	if err := NewSender(nil, testLinks()).Send(context.Background(), request(testChatID)); !isSkip(err) {
		t.Fatalf("no bot: want skip, got %v", err)
	}
	bot := &botServer{}
	if err := NewSender(newBot(t, bot), testLinks()).Send(context.Background(), request(0)); !isSkip(err) {
		t.Fatalf("no chat: want skip, got %v", err)
	}
	if len(bot.requests) != 0 {
		t.Fatal("no API call expected")
	}
}

func isSkip(err error) bool {
	var skip *delivery.SkipError
	return errors.As(err, &skip)
}

func TestTransportErrorNeverContainsToken(t *testing.T) {
	client := NewClient("http://127.0.0.1:1", testToken) // nothing listens on port 1
	err := client.SendMessage(context.Background(), SendMessageRequest{ChatID: 1, Text: "x"})
	var transportErr *TransportError
	if !errors.As(err, &transportErr) {
		t.Fatalf("want TransportError, got %v", err)
	}
	if strings.Contains(err.Error(), "SECRET") || strings.Contains(err.Error(), "bot123456") {
		t.Fatalf("token leaked: %v", err)
	}
}

type fakePublisher struct {
	mu     sync.Mutex
	events []envelope.Envelope
	err    error
}

func (f *fakePublisher) PublishEvent(_ context.Context, _ string, env envelope.Envelope) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.err != nil {
		return f.err
	}
	f.events = append(f.events, env)
	return nil
}

// fakeUpdates serves one batch of updates, then blocks until cancelled.
type fakeUpdates struct {
	mu      sync.Mutex
	batch   []Update
	offsets []int64
	replies []SendMessageRequest
	served  chan struct{}
}

func (f *fakeUpdates) GetUpdates(ctx context.Context, offset int64, _ int) ([]Update, error) {
	f.mu.Lock()
	f.offsets = append(f.offsets, offset)
	batch := f.batch
	f.batch = nil
	f.mu.Unlock()
	if batch != nil {
		return batch, nil
	}
	close(f.served)
	<-ctx.Done()
	return nil, ctx.Err()
}

func (f *fakeUpdates) SendMessage(_ context.Context, req SendMessageRequest) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.replies = append(f.replies, req)
	return nil
}

func privateMessage(updateID int64, text string) Update {
	return Update{UpdateID: updateID, Message: &Message{
		From: &User{ID: 42, Username: "student"}, Chat: Chat{ID: testChatID, Type: privateChatType}, Text: text,
	}}
}

func runLinker(t *testing.T, api *fakeUpdates, publisher *fakePublisher) {
	t.Helper()
	linker := &Linker{API: api, Publisher: publisher, LinkedTopic: "tc.telegram.linked.v1", PollTimeoutSec: 1,
		Retry: retry.Policy{Attempts: 2, InitialDelay: time.Millisecond, MaxDelay: time.Millisecond, Multiplier: 1},
		Log:   logging.Discard(), Now: time.Now}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- linker.Run(ctx) }()
	select {
	case <-api.served:
	case <-time.After(5 * time.Second):
		t.Fatal("linker did not poll")
	}
	cancel()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}

func TestLinkerPublishesLinkedEventAndReplies(t *testing.T) {
	api := &fakeUpdates{served: make(chan struct{}), batch: []Update{
		privateMessage(10, "/start "+linkCode),
		privateMessage(11, "/start"),
		privateMessage(12, "hello"),
		{UpdateID: 13, Message: &Message{From: &User{ID: 1}, Chat: Chat{ID: -100, Type: "group"}, Text: "/start " + linkCode}},
	}}
	publisher := &fakePublisher{}
	runLinker(t, api, publisher)
	if len(publisher.events) != 1 {
		t.Fatalf("want 1 linked event, got %d", len(publisher.events))
	}
	env := publisher.events[0]
	var linked Linked
	if err := json.Unmarshal(env.Payload, &linked); err != nil {
		t.Fatal(err)
	}
	if env.TenantID != envelope.NilTenantID || env.Type != TypeLinked || linked.LinkCode != linkCode ||
		linked.ChatID != testChatID || linked.TelegramUserID != 42 || linked.Username != "student" {
		t.Fatalf("unexpected event %+v %+v", env, linked)
	}
	if len(api.replies) != 2 || api.replies[0].Text != ReplyLinked || api.replies[1].Text != ReplyInstructions {
		t.Fatalf("unexpected replies %+v", api.replies)
	}
	if last := api.offsets[len(api.offsets)-1]; last != 14 {
		t.Fatalf("offset not advanced: %v", api.offsets)
	}
}

func TestLinkerRepliesFailureWhenPublishFails(t *testing.T) {
	api := &fakeUpdates{served: make(chan struct{}), batch: []Update{privateMessage(1, "/start@TutorCraftBot "+linkCode)}}
	runLinker(t, api, &fakePublisher{err: errors.New("broker down")})
	if len(api.replies) != 1 || api.replies[0].Text != ReplyLinkFailed {
		t.Fatalf("unexpected replies %+v", api.replies)
	}
}
