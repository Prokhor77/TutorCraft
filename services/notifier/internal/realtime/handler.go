package realtime

import (
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/tutorcraft/notifier/internal/auth"
)

// Connection parameters and close codes.
const (
	TokenQueryParam   = "token"
	SendBufferSize    = 32
	MaxConnsPerUser   = 10
	maxInboundBytes   = 512
	writeWait         = 10 * time.Second
	defaultPongWait   = 60 * time.Second
	pingPeriodPercent = 90
	bufferBytes       = 1024

	// CloseTokenExpired tells the client to reconnect with a fresh access token.
	CloseTokenExpired  = 4001
	reasonTokenExpired = "token expired"
	reasonTooMany      = "too many connections"
	reasonGoingAway    = "server closing connection"
)

// TokenValidator authenticates the ?token= query parameter.
type TokenValidator interface {
	Validate(token string) (auth.Principal, error)
}

// Handler serves GET /ws?token=<accessToken>.
type Handler struct {
	validator TokenValidator
	hub       *Hub
	upgrader  websocket.Upgrader
	pongWait  time.Duration
	log       *slog.Logger
}

// NewHandler creates the endpoint; allowedOrigin is WEB_ORIGIN.
func NewHandler(validator TokenValidator, hub *Hub, allowedOrigin string, log *slog.Logger) *Handler {
	return &Handler{
		validator: validator,
		hub:       hub,
		upgrader: websocket.Upgrader{
			ReadBufferSize: bufferBytes, WriteBufferSize: bufferBytes,
			CheckOrigin: originChecker(allowedOrigin),
		},
		pongWait: defaultPongWait,
		log:      log,
	}
}

// originChecker allows WEB_ORIGIN only. Requests without Origin come from
// non-browser clients, which cannot be abused for cross-site hijacking.
func originChecker(allowed string) func(*http.Request) bool {
	allowed = strings.TrimSuffix(allowed, "/")
	return func(r *http.Request) bool {
		origin := r.Header.Get("Origin")
		return origin == "" || strings.EqualFold(strings.TrimSuffix(origin, "/"), allowed)
	}
}

// ServeHTTP authenticates, upgrades and serves one connection. The token is
// never logged (it is part of the URL, so the URL is never logged either).
func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	principal, err := h.validator.Validate(r.URL.Query().Get(TokenQueryParam))
	if err != nil {
		http.Error(w, http.StatusText(http.StatusUnauthorized), http.StatusUnauthorized)
		return
	}
	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return // the upgrader has already replied (e.g. 403 for a foreign Origin)
	}
	client := NewClient(principal.UserID, principal.TenantID, SendBufferSize)
	if err := h.hub.Register(client); err != nil {
		closeWith(conn, websocket.ClosePolicyViolation, reasonTooMany)
		closeConn(conn)
		return
	}
	defer h.hub.Unregister(client)
	go h.writePump(conn, client, principal.ExpiresAt)
	h.readPump(conn, client)
}

func (h *Handler) pingPeriod() time.Duration {
	return h.pongWait * pingPeriodPercent / 100
}
