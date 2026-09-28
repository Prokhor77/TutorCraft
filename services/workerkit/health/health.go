// Package health serves /health/live and /health/ready (NFR-OBS-03).
package health

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"sync/atomic"
	"time"
)

const (
	LivePath       = "/health/live"
	ReadyPath      = "/health/ready"
	statusUp       = "UP"
	statusDown     = "DOWN"
	checkTimeout   = 2 * time.Second
	contentTypeKey = "Content-Type"
	contentJSON    = "application/json"
)

// Check reports whether a dependency is usable.
type Check struct {
	Name  string
	Probe func(ctx context.Context) error
}

// Probe answers health requests; readiness also runs the checks.
type Probe struct {
	checks       []Check
	shuttingDown atomic.Bool
}

// Register mounts the health endpoints on mux.
func Register(mux *http.ServeMux, checks ...Check) *Probe {
	p := &Probe{checks: checks}
	mux.HandleFunc("GET "+LivePath, p.live)
	mux.HandleFunc("GET "+ReadyPath, p.ready)
	return p
}

// MarkShuttingDown makes readiness fail so that traffic drains away.
func (p *Probe) MarkShuttingDown() {
	p.shuttingDown.Store(true)
}

type report struct {
	Status string            `json:"status"`
	Checks map[string]string `json:"checks,omitempty"`
}

func (p *Probe) live(w http.ResponseWriter, _ *http.Request) {
	write(w, http.StatusOK, report{Status: statusUp})
}

func (p *Probe) ready(w http.ResponseWriter, r *http.Request) {
	if p.shuttingDown.Load() {
		write(w, http.StatusServiceUnavailable, report{Status: statusDown})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), checkTimeout)
	defer cancel()
	result := report{Status: statusUp, Checks: map[string]string{}}
	for _, check := range p.checks {
		result.Checks[check.Name] = statusUp
		if err := check.Probe(ctx); err != nil {
			result.Checks[check.Name] = statusDown
			result.Status = statusDown
		}
	}
	code := http.StatusOK
	if result.Status == statusDown {
		code = http.StatusServiceUnavailable
	}
	write(w, code, result)
}

func write(w http.ResponseWriter, code int, body report) {
	w.Header().Set(contentTypeKey, contentJSON)
	w.WriteHeader(code)
	// A failed write means the client went away; nothing useful to do.
	_ = json.NewEncoder(w).Encode(body)
}

// TCPCheck succeeds when at least one of addrs accepts a TCP connection.
func TCPCheck(name string, addrs []string) Check {
	return Check{Name: name, Probe: func(ctx context.Context) error {
		var dialer net.Dialer
		errs := make([]error, 0, len(addrs))
		for _, addr := range addrs {
			conn, err := dialer.DialContext(ctx, "tcp", addr)
			if err == nil {
				return conn.Close()
			}
			errs = append(errs, err)
		}
		return errors.Join(errs...)
	}}
}
