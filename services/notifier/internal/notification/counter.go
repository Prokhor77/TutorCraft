package notification

import (
	"encoding/json"
	"errors"
	"regexp"
	"strings"
)

var (
	counterNamePattern = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)
	errInvalidCounter  = errors.New("invalid counter")
)

// Counter is a real-time badge value, carried in the body of a
// "counter" notification as {"name":"grading_queue","value":15}.
type Counter struct {
	Name  string `json:"name"`
	Value int64  `json:"value"`
}

// ParseCounter decodes and validates a counter body.
func ParseCounter(body string) (Counter, error) {
	decoder := json.NewDecoder(strings.NewReader(body))
	decoder.UseNumber()
	var raw struct {
		Name  string       `json:"name"`
		Value *json.Number `json:"value"`
	}
	if err := decoder.Decode(&raw); err != nil || raw.Value == nil || !counterNamePattern.MatchString(raw.Name) {
		return Counter{}, errInvalidCounter
	}
	value, err := raw.Value.Int64()
	if err != nil || strings.ContainsAny(raw.Value.String(), ".eE") {
		return Counter{}, errInvalidCounter
	}
	return Counter{Name: raw.Name, Value: value}, nil
}
