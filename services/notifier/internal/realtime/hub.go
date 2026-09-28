// Package realtime is the WebSocket side of the notifier: an in-memory hub
// of connections keyed by userId, the /ws endpoint and the "web" channel.
package realtime

import (
	"errors"
	"log/slog"
	"sync"
)

// ErrTooManyConnections is returned when a user already has the maximum number of tabs open.
var ErrTooManyConnections = errors.New("too many connections for user")

// Client is one connection's outbound side: a bounded send buffer.
// A client whose buffer is full is a slow consumer and gets disconnected.
type Client struct {
	UserID   string
	TenantID string
	send     chan []byte
	done     chan struct{}
	once     sync.Once
}

// NewClient creates a client with a send buffer of bufferSize messages.
func NewClient(userID, tenantID string, bufferSize int) *Client {
	return &Client{UserID: userID, TenantID: tenantID, send: make(chan []byte, bufferSize), done: make(chan struct{})}
}

// Messages yields queued outbound messages.
func (c *Client) Messages() <-chan []byte { return c.send }

// Done is closed when the client must be disconnected.
func (c *Client) Done() <-chan struct{} { return c.done }

// Close marks the client for disconnection (idempotent).
func (c *Client) Close() { c.once.Do(func() { close(c.done) }) }

// enqueue never blocks: a full buffer closes the client (drop slow consumer).
func (c *Client) enqueue(msg []byte) bool {
	select {
	case <-c.done:
		return false
	default:
	}
	select {
	case c.send <- msg:
		return true
	default:
		c.Close()
		return false
	}
}

// Hub tracks connected clients by userId (many tabs per user).
type Hub struct {
	mu         sync.Mutex
	byUser     map[string]map[*Client]struct{}
	maxPerUser int
	log        *slog.Logger
}

// NewHub creates a hub allowing maxPerUser connections per user.
func NewHub(maxPerUser int, log *slog.Logger) *Hub {
	return &Hub{byUser: map[string]map[*Client]struct{}{}, maxPerUser: maxPerUser, log: log}
}

// Register adds a client unless the user is at the connection limit.
func (h *Hub) Register(c *Client) error {
	h.mu.Lock()
	defer h.mu.Unlock()
	clients := h.byUser[c.UserID]
	if len(clients) >= h.maxPerUser {
		return ErrTooManyConnections
	}
	if clients == nil {
		clients = map[*Client]struct{}{}
		h.byUser[c.UserID] = clients
	}
	clients[c] = struct{}{}
	return nil
}

// Unregister removes a client (idempotent).
func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	clients := h.byUser[c.UserID]
	delete(clients, c)
	if len(clients) == 0 {
		delete(h.byUser, c.UserID)
	}
}

// Publish queues msg for every connection of the user within the tenant and
// returns how many accepted it. Slow consumers are disconnected.
func (h *Hub) Publish(userID, tenantID string, msg []byte) int {
	delivered := 0
	for _, c := range h.clientsOf(userID) {
		if c.TenantID != tenantID {
			continue
		}
		if c.enqueue(msg) {
			delivered++
			continue
		}
		h.Unregister(c)
		h.log.Warn("slow websocket consumer disconnected", slog.String("userId", userID))
	}
	return delivered
}

func (h *Hub) clientsOf(userID string) []*Client {
	h.mu.Lock()
	defer h.mu.Unlock()
	clients := make([]*Client, 0, len(h.byUser[userID]))
	for c := range h.byUser[userID] {
		clients = append(clients, c)
	}
	return clients
}

// CloseAll disconnects everyone (shutdown).
func (h *Hub) CloseAll() {
	h.mu.Lock()
	defer h.mu.Unlock()
	for userID, clients := range h.byUser {
		for c := range clients {
			c.Close()
		}
		delete(h.byUser, userID)
	}
}

// Count returns the number of registered connections.
func (h *Hub) Count() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	total := 0
	for _, clients := range h.byUser {
		total += len(clients)
	}
	return total
}
