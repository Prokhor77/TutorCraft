// Package logging builds the JSON slog logger used by all Go services
// (NFR-OBS-01). Callers must log identifiers only, never secrets or PII.
package logging

import (
	"fmt"
	"io"
	"log/slog"
	"strings"
)

// New returns a JSON logger at the given level ("debug", "info", "warn", "error").
func New(w io.Writer, service, level string) (*slog.Logger, error) {
	var lvl slog.Level
	if err := lvl.UnmarshalText([]byte(strings.ToUpper(level))); err != nil {
		return nil, fmt.Errorf("invalid log level: %w", err)
	}
	handler := slog.NewJSONHandler(w, &slog.HandlerOptions{Level: lvl})
	return slog.New(handler).With(slog.String("service", service)), nil
}

// Discard returns a logger that drops everything (tests).
func Discard() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}
