// Package envconfig reads configuration from environment variables and
// accumulates every problem so that a service fails fast with a full report.
//
// Error messages name variables but never include their values (secrets).
package envconfig

import (
	"errors"
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

const listSeparator = ","

// Loader reads typed values; check Err after reading everything.
type Loader struct {
	lookup func(string) (string, bool)
	errs   []error
}

// FromOS reads the process environment.
func FromOS() *Loader {
	return &Loader{lookup: os.LookupEnv}
}

// FromMap reads a fixed map (tests).
func FromMap(values map[string]string) *Loader {
	return &Loader{lookup: func(key string) (string, bool) {
		v, ok := values[key]
		return v, ok
	}}
}

// Err returns all accumulated problems, or nil.
func (l *Loader) Err() error {
	return errors.Join(l.errs...)
}

// Fail records a custom validation problem.
func (l *Loader) Fail(key, problem string) {
	l.errs = append(l.errs, fmt.Errorf("%s %s", key, problem))
}

func (l *Loader) raw(key string) (string, bool) {
	v, ok := l.lookup(key)
	v = strings.TrimSpace(v)
	return v, ok && v != ""
}

// String returns the value or def when unset.
func (l *Loader) String(key, def string) string {
	if v, ok := l.raw(key); ok {
		return v
	}
	return def
}

// RequiredString returns the value and records an error when unset.
func (l *Loader) RequiredString(key string) string {
	v, ok := l.raw(key)
	if !ok {
		l.Fail(key, "is required")
	}
	return v
}

// Int returns an integer within [minValue, maxValue], or def when unset.
func (l *Loader) Int(key string, def, minValue, maxValue int) int {
	return int(l.Int64(key, int64(def), int64(minValue), int64(maxValue)))
}

// Int64 returns an integer within [minValue, maxValue], or def when unset.
func (l *Loader) Int64(key string, def, minValue, maxValue int64) int64 {
	v, ok := l.raw(key)
	if !ok {
		return def
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil || n < minValue || n > maxValue {
		l.Fail(key, fmt.Sprintf("must be an integer in [%d, %d]", minValue, maxValue))
		return def
	}
	return n
}

// Duration returns a Go duration (e.g. "30m") within [minValue, maxValue], or def.
func (l *Loader) Duration(key string, def, minValue, maxValue time.Duration) time.Duration {
	v, ok := l.raw(key)
	if !ok {
		return def
	}
	d, err := time.ParseDuration(v)
	if err != nil || d < minValue || d > maxValue {
		l.Fail(key, fmt.Sprintf("must be a duration in [%s, %s]", minValue, maxValue))
		return def
	}
	return d
}

// RequiredList returns a comma-separated list with blanks removed.
func (l *Loader) RequiredList(key string) []string {
	var items []string
	for _, item := range strings.Split(l.RequiredString(key), listSeparator) {
		if trimmed := strings.TrimSpace(item); trimmed != "" {
			items = append(items, trimmed)
		}
	}
	if len(items) == 0 && len(l.errs) == 0 {
		l.Fail(key, "must list at least one item")
	}
	return items
}

// HTTPURL parses an absolute http(s) URL; required when def is empty.
func (l *Loader) HTTPURL(key, def string) *url.URL {
	v := l.String(key, def)
	if v == "" {
		l.Fail(key, "is required")
		return nil
	}
	u, err := url.Parse(v)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		l.Fail(key, "must be an absolute http(s) URL")
		return nil
	}
	return u
}
