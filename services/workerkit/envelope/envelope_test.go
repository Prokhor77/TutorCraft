package envelope

import (
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/tutorcraft/workerkit/validate"
)

const (
	testEventID  = "0192f3c1-7b2a-7c3d-8e4f-123456789abc"
	testTenantID = "0192f3c1-0000-7000-8000-000000000001"
)

func validJSON() string {
	return `{"eventId":"` + testEventID + `","type":"media.video-uploaded","version":1,
		"occurredAt":"2026-09-27T18:00:00.123456Z","tenantId":"` + testTenantID + `",
		"payload":{"fileId":"x"},"unknownField":true}`
}

func TestDecodeValidEnvelopeIgnoresUnknownFields(t *testing.T) {
	env, err := Decode([]byte(validJSON()))
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	if env.EventID != testEventID || env.TenantID != testTenantID || env.Version != 1 {
		t.Fatalf("unexpected envelope %+v", env)
	}
	if env.OccurredAt.IsZero() || string(env.Payload) != `{"fileId":"x"}` {
		t.Fatalf("unexpected occurredAt/payload %+v", env)
	}
}

func TestDecodeRejectsInvalidEnvelopes(t *testing.T) {
	cases := map[string]string{
		"not json":        `{`,
		"version 2":       `{"eventId":"` + testEventID + `","type":"t","version":2,"occurredAt":"2026-09-27T18:00:00Z","tenantId":"` + testTenantID + `","payload":{}}`,
		"missing version": `{"eventId":"` + testEventID + `","type":"t","occurredAt":"2026-09-27T18:00:00Z","tenantId":"` + testTenantID + `","payload":{}}`,
		"bad eventId":     `{"eventId":"1","type":"t","version":1,"occurredAt":"2026-09-27T18:00:00Z","tenantId":"` + testTenantID + `","payload":{}}`,
		"bad time":        `{"eventId":"` + testEventID + `","type":"t","version":1,"occurredAt":"yesterday","tenantId":"` + testTenantID + `","payload":{}}`,
		"payload array":   `{"eventId":"` + testEventID + `","type":"t","version":1,"occurredAt":"2026-09-27T18:00:00Z","tenantId":"` + testTenantID + `","payload":[]}`,
		"missing tenant":  `{"eventId":"` + testEventID + `","type":"t","version":1,"occurredAt":"2026-09-27T18:00:00Z","payload":{}}`,
	}
	for name, input := range cases {
		if _, err := Decode([]byte(input)); err == nil {
			t.Errorf("%s: expected error", name)
		}
	}
}

func TestNewAndEncodeRoundTrip(t *testing.T) {
	now := time.Date(2026, 9, 27, 18, 0, 0, 0, time.UTC)
	env, err := New("media.video-processed", testTenantID, map[string]string{"fileId": "f"}, now)
	if err != nil {
		t.Fatalf("new: %v", err)
	}
	data, err := env.Encode()
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	decoded, err := Decode(data)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	if decoded.EventID != env.EventID || !decoded.OccurredAt.Equal(now) || decoded.Type != env.Type {
		t.Fatalf("round trip mismatch: %+v vs %+v", decoded, env)
	}
}

func TestDecodePayloadWrapsErrorsAsValidation(t *testing.T) {
	var dst struct {
		Size int64 `json:"size"`
	}
	err := DecodePayload(json.RawMessage(`{"size":"big"}`), &dst)
	var verr *validate.Error
	if !errors.As(err, &verr) {
		t.Fatalf("want validation error, got %v", err)
	}
}
