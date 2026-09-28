package health

import (
	"context"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
)

func serve(t *testing.T, mux *http.ServeMux, path string) int {
	t.Helper()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, path, nil))
	return rec.Code
}

func TestLiveAndReady(t *testing.T) {
	mux := http.NewServeMux()
	healthy := true
	probe := Register(mux, Check{Name: "dep", Probe: func(context.Context) error {
		if healthy {
			return nil
		}
		return errors.New("down")
	}})
	if serve(t, mux, LivePath) != http.StatusOK || serve(t, mux, ReadyPath) != http.StatusOK {
		t.Fatal("expected UP")
	}
	healthy = false
	if serve(t, mux, ReadyPath) != http.StatusServiceUnavailable {
		t.Fatal("expected DOWN when check fails")
	}
	healthy = true
	probe.MarkShuttingDown()
	if serve(t, mux, ReadyPath) != http.StatusServiceUnavailable || serve(t, mux, LivePath) != http.StatusOK {
		t.Fatal("shutting down must fail readiness only")
	}
}

func TestTCPCheck(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	ok := TCPCheck("kafka", []string{"127.0.0.1:1", listener.Addr().String()})
	if err := ok.Probe(context.Background()); err != nil {
		t.Fatalf("expected success, got %v", err)
	}
	bad := TCPCheck("kafka", []string{"127.0.0.1:1"})
	if err := bad.Probe(context.Background()); err == nil {
		t.Fatal("expected failure")
	}
}
