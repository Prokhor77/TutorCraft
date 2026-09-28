package realtime

import (
	"time"

	"github.com/gorilla/websocket"
)

// readPump consumes client frames (only control frames matter: pongs keep
// the connection alive) until the connection fails or times out.
func (h *Handler) readPump(conn *websocket.Conn, client *Client) {
	defer client.Close()
	conn.SetReadLimit(maxInboundBytes)
	extend := func(string) error { return conn.SetReadDeadline(time.Now().Add(h.pongWait)) }
	if err := extend(""); err != nil {
		return
	}
	conn.SetPongHandler(extend)
	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			return
		}
	}
}

// writePump is the connection's only writer: queued messages, keepalive
// pings, and the close frame when the token expires or the client is dropped.
func (h *Handler) writePump(conn *websocket.Conn, client *Client, tokenExpiresAt time.Time) {
	ping := time.NewTicker(h.pingPeriod())
	expiry := time.NewTimer(time.Until(tokenExpiresAt))
	defer func() {
		ping.Stop()
		expiry.Stop()
		closeConn(conn)
	}()
	for {
		select {
		case msg := <-client.Messages():
			if writeFrame(conn, websocket.TextMessage, msg) != nil {
				client.Close()
				return
			}
		case <-ping.C:
			if writeFrame(conn, websocket.PingMessage, nil) != nil {
				client.Close()
				return
			}
		case <-expiry.C:
			client.Close()
			closeWith(conn, CloseTokenExpired, reasonTokenExpired)
			return
		case <-client.Done():
			closeWith(conn, websocket.CloseGoingAway, reasonGoingAway)
			return
		}
	}
}

func writeFrame(conn *websocket.Conn, messageType int, data []byte) error {
	if err := conn.SetWriteDeadline(time.Now().Add(writeWait)); err != nil {
		return err
	}
	return conn.WriteMessage(messageType, data)
}

// closeWith sends a close frame; failure means the peer is already gone,
// and the connection is closed right after either way.
func closeWith(conn *websocket.Conn, code int, reason string) {
	deadline := time.Now().Add(writeWait)
	_ = conn.WriteControl(websocket.CloseMessage, websocket.FormatCloseMessage(code, reason), deadline)
}

// closeConn releases the socket; an error here only means it was already closed.
func closeConn(conn *websocket.Conn) {
	_ = conn.Close()
}
