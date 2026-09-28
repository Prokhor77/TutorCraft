// Package envelope implements the Kafka event envelope shared with core-api
// (docs/events/README.md, ADR-005):
//
//	{eventId, type, version, occurredAt, tenantId, payload}
package envelope

import (
	"bytes"
	"encoding/json"
	"fmt"
	"time"

	"github.com/tutorcraft/workerkit/ids"
	"github.com/tutorcraft/workerkit/validate"
)

// SupportedVersion is the only envelope version this code understands.
const SupportedVersion = 1

// NilTenantID is used for events whose tenant is not known to the producer
// (e.g. Telegram linking, where core-api resolves the tenant by link code).
const NilTenantID = "00000000-0000-0000-0000-000000000000"

// Envelope is a decoded event. Payload stays raw: each handler decodes and
// validates its own payload type.
type Envelope struct {
	EventID    string          `json:"eventId"`
	Type       string          `json:"type"`
	Version    int             `json:"version"`
	OccurredAt time.Time       `json:"occurredAt"`
	TenantID   string          `json:"tenantId"`
	Payload    json.RawMessage `json:"payload"`
}

// wire mirrors Envelope with optional fields so that missing values can be detected.
type wire struct {
	EventID    string          `json:"eventId"`
	Type       string          `json:"type"`
	Version    *int            `json:"version"`
	OccurredAt string          `json:"occurredAt"`
	TenantID   string          `json:"tenantId"`
	Payload    json.RawMessage `json:"payload"`
}

// Decode parses and validates an envelope. Unknown fields are ignored.
func Decode(data []byte) (Envelope, error) {
	var w wire
	if err := json.Unmarshal(data, &w); err != nil {
		return Envelope{}, fmt.Errorf("envelope is not valid JSON: %w", err)
	}
	var c validate.Collector
	c.UUID("eventId", w.EventID)
	c.Required("type", w.Type)
	c.UUID("tenantId", w.TenantID)
	c.Check(w.Version != nil && *w.Version == SupportedVersion, "version", "must be 1")
	occurredAt, err := time.Parse(time.RFC3339Nano, w.OccurredAt)
	c.Check(err == nil, "occurredAt", "must be an RFC 3339 timestamp")
	c.Check(isJSONObject(w.Payload), "payload", "must be a JSON object")
	if err := c.Err(); err != nil {
		return Envelope{}, err
	}
	return Envelope{
		EventID: w.EventID, Type: w.Type, Version: *w.Version,
		OccurredAt: occurredAt, TenantID: w.TenantID, Payload: w.Payload,
	}, nil
}

// New builds an envelope with a fresh UUIDv7 eventId.
func New(eventType, tenantID string, payload any, now time.Time) (Envelope, error) {
	raw, err := json.Marshal(payload)
	if err != nil {
		return Envelope{}, fmt.Errorf("marshal %s payload: %w", eventType, err)
	}
	return Envelope{
		EventID:    ids.NewV7At(now),
		Type:       eventType,
		Version:    SupportedVersion,
		OccurredAt: now.UTC(),
		TenantID:   tenantID,
		Payload:    raw,
	}, nil
}

// Encode serialises the envelope to JSON.
func (e Envelope) Encode() ([]byte, error) {
	return json.Marshal(e)
}

// DecodePayload unmarshals the payload into dst.
func DecodePayload(raw json.RawMessage, dst any) error {
	if err := json.Unmarshal(raw, dst); err != nil {
		return &validate.Error{Fields: []validate.FieldError{{Field: "payload", Problem: validate.ProblemInvalid}}}
	}
	return nil
}

func isJSONObject(raw json.RawMessage) bool {
	trimmed := bytes.TrimSpace(raw)
	return len(trimmed) > 0 && trimmed[0] == '{'
}
