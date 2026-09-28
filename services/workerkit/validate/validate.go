// Package validate collects field-level problems of incoming messages.
//
// Error messages name fields only and never echo field values, so they are
// safe to log and to put into dead-letter headers (NFR-SEC-11).
package validate

import (
	"regexp"
	"strings"
)

const (
	ProblemRequired = "is required"
	ProblemUUID     = "must be a UUID"
	ProblemTooLong  = "is too long"
	ProblemInvalid  = "is invalid"
	problemOneOf    = "must be one of: "
)

var uuidPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// IsUUID reports whether s is a canonical textual UUID (any version).
func IsUUID(s string) bool {
	return uuidPattern.MatchString(s)
}

// FieldError describes one invalid field.
type FieldError struct {
	Field   string
	Problem string
}

// Error is returned when a message fails validation.
type Error struct {
	Fields []FieldError
}

func (e *Error) Error() string {
	parts := make([]string, 0, len(e.Fields))
	for _, f := range e.Fields {
		parts = append(parts, f.Field+" "+f.Problem)
	}
	return "invalid message: " + strings.Join(parts, "; ")
}

// Collector accumulates field problems; the zero value is ready to use.
type Collector struct {
	fields []FieldError
}

// Add records a problem for a field.
func (c *Collector) Add(field, problem string) {
	c.fields = append(c.fields, FieldError{Field: field, Problem: problem})
}

// Check records the problem when ok is false.
func (c *Collector) Check(ok bool, field, problem string) {
	if !ok {
		c.Add(field, problem)
	}
}

// Required checks that a string field is present and not blank.
func (c *Collector) Required(field, value string) bool {
	ok := strings.TrimSpace(value) != ""
	c.Check(ok, field, ProblemRequired)
	return ok
}

// UUID checks that a field is present and is a UUID.
func (c *Collector) UUID(field, value string) {
	if c.Required(field, value) {
		c.Check(IsUUID(value), field, ProblemUUID)
	}
}

// MaxLen checks the length of a string field in characters (runes).
func (c *Collector) MaxLen(field, value string, maxRunes int) {
	c.Check(len([]rune(value)) <= maxRunes, field, ProblemTooLong)
}

// OneOf checks that a field equals one of the allowed values.
func (c *Collector) OneOf(field, value string, allowed ...string) {
	for _, candidate := range allowed {
		if value == candidate {
			return
		}
	}
	c.Add(field, problemOneOf+strings.Join(allowed, ", "))
}

// Err returns the accumulated problems as *Error, or nil when there are none.
func (c *Collector) Err() error {
	if len(c.fields) == 0 {
		return nil
	}
	return &Error{Fields: c.fields}
}
