package notification

import (
	"encoding/json"
	"errors"
	"net/url"
	"strings"
	"testing"

	"github.com/tutorcraft/workerkit/validate"
)

const (
	testNotificationID = "0192f3c1-7b2a-7c3d-8e4f-123456789abc"
	testUserID         = "0192f3c1-0000-7000-8000-000000000002"
)

func validRequest() map[string]any {
	return map[string]any{
		"notificationId": testNotificationID, "userId": testUserID, "category": "grade_published",
		"channels": []string{"email", "telegram", "web"}, "title": "Оценка опубликована",
		"body": "Ваша работа проверена", "link": "/courses/1", "locale": "ru",
		"email": "student@example.com", "telegramChatId": 123456789, "actionLabel": "Открыть курс", "futureField": 1,
	}
}

func decodeMap(t *testing.T, m map[string]any) (Requested, error) {
	t.Helper()
	raw, err := json.Marshal(m)
	if err != nil {
		t.Fatal(err)
	}
	return Decode(raw)
}

func TestDecodeValid(t *testing.T) {
	r, err := decodeMap(t, validRequest())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if r.TelegramChatID != 123456789 || len(r.Channels) != 3 || r.Language() != "ru" || r.ActionLabel != "Открыть курс" {
		t.Fatalf("unexpected request %+v", r)
	}
}

func TestDecodeOptionalFieldsMayBeAbsent(t *testing.T) {
	m := validRequest()
	for _, key := range []string{"email", "telegramChatId", "link", "locale", "body", "actionLabel"} {
		delete(m, key)
	}
	if _, err := decodeMap(t, m); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

func TestDecodeRejectsInvalid(t *testing.T) {
	cases := map[string]func(m map[string]any){
		"bad notificationId": func(m map[string]any) { m["notificationId"] = "1" },
		"missing userId":     func(m map[string]any) { delete(m, "userId") },
		"no channels":        func(m map[string]any) { m["channels"] = []string{} },
		"unknown channel":    func(m map[string]any) { m["channels"] = []string{"sms"} },
		"duplicate channel":  func(m map[string]any) { m["channels"] = []string{"web", "web"} },
		"blank title":        func(m map[string]any) { m["title"] = " " },
		"long title":         func(m map[string]any) { m["title"] = strings.Repeat("я", MaxTitleRunes+1) },
		"javascript link":    func(m map[string]any) { m["link"] = "javascript:alert(1)" },
		"protocol-relative":  func(m map[string]any) { m["link"] = "//evil.example" },
		"bad email":          func(m map[string]any) { m["email"] = "Name <a@b.c>" },
		"bad category":       func(m map[string]any) { m["category"] = "Grade Published" },
		"bad locale":         func(m map[string]any) { m["locale"] = "russian" },
		"chat id as string":  func(m map[string]any) { m["telegramChatId"] = "123" },
		"counter bad body":   func(m map[string]any) { m["category"] = "counter"; m["body"] = "15" },
		"long action label":  func(m map[string]any) { m["actionLabel"] = strings.Repeat("я", MaxActionRunes+1) },
	}
	for name, mutate := range cases {
		m := validRequest()
		mutate(m)
		_, err := decodeMap(t, m)
		var verr *validate.Error
		if !errors.As(err, &verr) {
			t.Errorf("%s: want validation error, got %v", name, err)
		}
	}
}

func TestParseCounter(t *testing.T) {
	c, err := ParseCounter(`{"name":"grading_queue","value":15}`)
	if err != nil || c.Name != "grading_queue" || c.Value != 15 {
		t.Fatalf("got %+v, %v", c, err)
	}
	for _, bad := range []string{`{"name":"x"}`, `{"name":"Bad Name","value":1}`, `{"name":"x","value":1.5}`, `[]`, ``} {
		if _, err := ParseCounter(bad); err == nil {
			t.Errorf("ParseCounter(%q) should fail", bad)
		}
	}
}

func TestLinkResolver(t *testing.T) {
	base, _ := url.Parse("https://app.tutorcraft.ru/")
	r := NewLinkResolver(base)
	cases := map[string]string{
		"/courses/1":            "https://app.tutorcraft.ru/courses/1",
		"https://other.example": "https://other.example",
		"":                      "",
	}
	for in, want := range cases {
		if got := r.Absolute(in); got != want {
			t.Errorf("Absolute(%q) = %q, want %q", in, got, want)
		}
	}
}

// Shape produced by core-api NotificationsApiImpl.NotifyRequested with Jackson
// default-property-inclusion=always (explicit nulls, absolute link).
func TestDecodeCoreAPIPayloadWithNulls(t *testing.T) {
	raw := []byte(`{"notificationId":"` + testNotificationID + `","userId":"` + testUserID + `",
		"category":"video_ready","channels":["email","web"],"title":"Видео готово","body":"Файл lesson.mp4 обработан",
		"link":null,"locale":"ru","email":null,"telegramChatId":null}`)
	r, err := Decode(raw)
	if err != nil {
		t.Fatalf("core payload rejected: %v", err)
	}
	if r.Link != "" || r.Email != "" || r.TelegramChatID != 0 || len(r.Channels) != 2 {
		t.Fatalf("unexpected decode %+v", r)
	}
}
