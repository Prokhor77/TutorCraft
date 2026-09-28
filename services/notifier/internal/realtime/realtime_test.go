package realtime

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/tutorcraft/notifier/internal/auth"
	"github.com/tutorcraft/notifier/internal/delivery"
	"github.com/tutorcraft/notifier/internal/notification"
	"github.com/tutorcraft/workerkit/logging"
)

const (
	userA   = "0192f3c1-0000-7000-8000-00000000000a"
	userB   = "0192f3c1-0000-7000-8000-00000000000b"
	tenant1 = "0192f3c1-0000-7000-8000-000000000001"
	tenant2 = "0192f3c1-0000-7000-8000-000000000002"
	origin  = "http://localhost:3000"
)

func TestHubFanOutToAllTabsOfUserInTenant(t *testing.T) {
	hub := NewHub(MaxConnsPerUser, logging.Discard())
	tab1, tab2 := NewClient(userA, tenant1, 4), NewClient(userA, tenant1, 4)
	other, otherTenant := NewClient(userB, tenant1, 4), NewClient(userA, tenant2, 4)
	for _, c := range []*Client{tab1, tab2, other, otherTenant} {
		if err := hub.Register(c); err != nil {
			t.Fatal(err)
		}
	}
	if n := hub.Publish(userA, tenant1, []byte("hi")); n != 2 {
		t.Fatalf("delivered to %d connections, want 2", n)
	}
	if len(tab1.Messages()) != 1 || len(tab2.Messages()) != 1 || len(other.Messages()) != 0 || len(otherTenant.Messages()) != 0 {
		t.Fatal("fan-out reached wrong clients")
	}
}

func TestHubDropsSlowConsumer(t *testing.T) {
	hub := NewHub(MaxConnsPerUser, logging.Discard())
	slow, fast := NewClient(userA, tenant1, 1), NewClient(userA, tenant1, 10)
	_ = hub.Register(slow)
	_ = hub.Register(fast)
	hub.Publish(userA, tenant1, []byte("1"))
	if n := hub.Publish(userA, tenant1, []byte("2")); n != 1 {
		t.Fatalf("delivered %d, want 1 (slow consumer dropped)", n)
	}
	select {
	case <-slow.Done():
	default:
		t.Fatal("slow consumer must be closed")
	}
	if hub.Count() != 1 || len(fast.Messages()) != 2 {
		t.Fatalf("count=%d fast=%d", hub.Count(), len(fast.Messages()))
	}
}

func TestHubLimitsConnectionsPerUser(t *testing.T) {
	hub := NewHub(2, logging.Discard())
	_ = hub.Register(NewClient(userA, tenant1, 1))
	third := NewClient(userA, tenant1, 1)
	_ = hub.Register(NewClient(userA, tenant1, 1))
	if err := hub.Register(third); !errors.Is(err, ErrTooManyConnections) {
		t.Fatalf("want ErrTooManyConnections, got %v", err)
	}
	if err := hub.Register(NewClient(userB, tenant1, 1)); err != nil {
		t.Fatal("limit must be per user")
	}
	hub.CloseAll()
	if hub.Count() != 0 {
		t.Fatal("CloseAll must remove everyone")
	}
}

func TestEncodeMessage(t *testing.T) {
	msg, err := EncodeMessage(notification.Requested{NotificationID: "n1", Title: "T", Body: "B", Link: "/x", Category: "grade_published"})
	if err != nil || string(msg) != `{"type":"notification","data":{"id":"n1","title":"T","body":"B","link":"/x","category":"grade_published"}}` {
		t.Fatalf("notification message %s, %v", msg, err)
	}
	msg, err = EncodeMessage(notification.Requested{Category: "counter", Body: `{"name":"grading_queue","value":15}`})
	if err != nil || string(msg) != `{"type":"counter","data":{"name":"grading_queue","value":15}}` {
		t.Fatalf("counter message %s, %v", msg, err)
	}
}

func TestWebSenderSkipsOfflineUser(t *testing.T) {
	hub := NewHub(MaxConnsPerUser, logging.Discard())
	sender := NewSender(hub)
	req := delivery.Request{TenantID: tenant1, Notification: notification.Requested{UserID: userA, Title: "t", Category: "x"}}
	var skip *delivery.SkipError
	if err := sender.Send(context.Background(), req); !errors.As(err, &skip) {
		t.Fatalf("want skip, got %v", err)
	}
	client := NewClient(userA, tenant1, 1)
	_ = hub.Register(client)
	if err := sender.Send(context.Background(), req); err != nil {
		t.Fatal(err)
	}
}

// fakeValidator accepts "good" and "short" (expires in 200ms).
type fakeValidator struct{}

func (fakeValidator) Validate(token string) (auth.Principal, error) {
	switch token {
	case "good":
		return auth.Principal{UserID: userA, TenantID: tenant1, ExpiresAt: time.Now().Add(time.Hour)}, nil
	case "short":
		return auth.Principal{UserID: userA, TenantID: tenant1, ExpiresAt: time.Now().Add(200 * time.Millisecond)}, nil
	}
	return auth.Principal{}, auth.ErrInvalidToken
}

func startServer(t *testing.T) (*Hub, string) {
	t.Helper()
	hub := NewHub(MaxConnsPerUser, logging.Discard())
	server := httptest.NewServer(NewHandler(fakeValidator{}, hub, origin, logging.Discard()))
	t.Cleanup(func() {
		hub.CloseAll()
		server.Close()
	})
	return hub, "ws" + strings.TrimPrefix(server.URL, "http")
}

func dial(t *testing.T, url, token, originHeader string) (*websocket.Conn, *http.Response, error) {
	t.Helper()
	header := http.Header{}
	if originHeader != "" {
		header.Set("Origin", originHeader)
	}
	return websocket.DefaultDialer.Dial(url+"?token="+token, header)
}

func waitForConnections(t *testing.T, hub *Hub, want int) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for hub.Count() != want {
		if time.Now().After(deadline) {
			t.Fatalf("connections = %d, want %d", hub.Count(), want)
		}
		time.Sleep(5 * time.Millisecond)
	}
}

func TestWebSocketDeliversPublishedMessage(t *testing.T) {
	hub, url := startServer(t)
	conn, _, err := dial(t, url, "good", origin)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	waitForConnections(t, hub, 1)
	hub.Publish(userA, tenant1, []byte(`{"type":"notification"}`))
	_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	_, data, err := conn.ReadMessage()
	var decoded map[string]any
	if err != nil || json.Unmarshal(data, &decoded) != nil || decoded["type"] != "notification" {
		t.Fatalf("read %s, %v", data, err)
	}
}

func TestWebSocketRejectsBadTokenAndForeignOrigin(t *testing.T) {
	_, url := startServer(t)
	if _, resp, err := dial(t, url, "bad", origin); err == nil || resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("bad token: want 401, got %v %v", resp, err)
	}
	if _, resp, err := dial(t, url, "good", "https://evil.example"); err == nil || resp.StatusCode != http.StatusForbidden {
		t.Fatalf("foreign origin: want 403, got %v %v", resp, err)
	}
}

func TestWebSocketClosesWhenTokenExpires(t *testing.T) {
	hub, url := startServer(t)
	conn, _, err := dial(t, url, "short", origin)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	_ = conn.SetReadDeadline(time.Now().Add(3 * time.Second))
	_, _, err = conn.ReadMessage()
	if !websocket.IsCloseError(err, CloseTokenExpired) {
		t.Fatalf("want close %d, got %v", CloseTokenExpired, err)
	}
	waitForConnections(t, hub, 0)
}
