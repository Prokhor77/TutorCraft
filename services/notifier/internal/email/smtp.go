package email

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"net"
	"net/smtp"
	"net/textproto"
	"strconv"
	"time"
)

const (
	extensionStartTLS  = "STARTTLS"
	extensionAuth      = "AUTH"
	permanentCodeFloor = 500
)

// ErrAuthUnsupported means credentials are configured but the server offers no AUTH.
var ErrAuthUnsupported = errors.New("smtp server does not support AUTH")

// SMTPTransport sends raw messages with net/smtp. With ImplicitTLS the
// connection is TLS from the start (SMTPS, port 465); otherwise STARTTLS is
// used whenever the server offers it (Mailpit on 1025 does not, so it stays
// plain locally).
type SMTPTransport struct {
	Host        string
	Port        int
	Username    string
	Password    string
	Timeout     time.Duration
	ImplicitTLS bool
}

// Send delivers msg from → to within Timeout (or ctx, whichever ends first).
func (t SMTPTransport) Send(ctx context.Context, from string, to []string, msg []byte) error {
	ctx, cancel := context.WithTimeout(ctx, t.Timeout)
	defer cancel()
	conn, err := t.dial(ctx)
	if err != nil {
		return err
	}
	stop := context.AfterFunc(ctx, func() { _ = conn.Close() }) // unblock I/O on cancel
	defer stop()
	if deadline, ok := ctx.Deadline(); ok {
		if err := conn.SetDeadline(deadline); err != nil {
			return errors.Join(fmt.Errorf("smtp deadline: %w", err), conn.Close())
		}
	}
	client, err := smtp.NewClient(conn, t.Host)
	if err != nil {
		return errors.Join(fmt.Errorf("smtp greeting: %w", err), conn.Close())
	}
	// After QUIT the server has closed the connection; Close only frees resources.
	defer func() { _ = client.Close() }()
	return t.session(client, from, to, msg)
}

// dial opens the TCP connection, wrapped in TLS for SMTPS. net/smtp detects a
// *tls.Conn, so AUTH PLAIN is allowed and STARTTLS is not offered again.
func (t SMTPTransport) dial(ctx context.Context) (net.Conn, error) {
	address := net.JoinHostPort(t.Host, strconv.Itoa(t.Port))
	if t.ImplicitTLS {
		dialer := tls.Dialer{Config: t.tlsConfig()}
		conn, err := dialer.DialContext(ctx, "tcp", address)
		if err != nil {
			return nil, fmt.Errorf("smtps connect: %w", err)
		}
		return conn, nil
	}
	var dialer net.Dialer
	conn, err := dialer.DialContext(ctx, "tcp", address)
	if err != nil {
		return nil, fmt.Errorf("smtp connect: %w", err)
	}
	return conn, nil
}

func (t SMTPTransport) tlsConfig() *tls.Config {
	return &tls.Config{ServerName: t.Host, MinVersion: tls.VersionTLS12}
}

func (t SMTPTransport) session(client *smtp.Client, from string, to []string, msg []byte) error {
	if err := t.secure(client); err != nil {
		return err
	}
	if err := client.Mail(from); err != nil {
		return fmt.Errorf("smtp MAIL FROM: %w", err)
	}
	for _, recipient := range to {
		if err := client.Rcpt(recipient); err != nil {
			return fmt.Errorf("smtp RCPT TO: %w", err)
		}
	}
	data, err := client.Data()
	if err != nil {
		return fmt.Errorf("smtp DATA: %w", err)
	}
	if _, err := data.Write(msg); err != nil {
		return errors.Join(fmt.Errorf("smtp write: %w", err), data.Close())
	}
	if err := data.Close(); err != nil {
		return fmt.Errorf("smtp end of data: %w", err)
	}
	return client.Quit()
}

func (t SMTPTransport) secure(client *smtp.Client) error {
	if ok, _ := client.Extension(extensionStartTLS); ok {
		if err := client.StartTLS(t.tlsConfig()); err != nil {
			return fmt.Errorf("smtp STARTTLS: %w", err)
		}
	}
	if t.Username == "" {
		return nil
	}
	if ok, _ := client.Extension(extensionAuth); !ok {
		return ErrAuthUnsupported
	}
	// PlainAuth refuses to send credentials over an unencrypted non-localhost connection.
	if err := client.Auth(smtp.PlainAuth("", t.Username, t.Password, t.Host)); err != nil {
		return fmt.Errorf("smtp AUTH: %w", err)
	}
	return nil
}

// ReplyError is an SMTP error reply reduced to its code: server texts often
// echo the recipient address, which must not reach logs or events.
type ReplyError struct {
	Code int
}

func (e *ReplyError) Error() string {
	return "smtp server replied with code " + strconv.Itoa(e.Code)
}

// IsPermanent reports whether the reply is a 5xx (permanent) failure.
func (e *ReplyError) IsPermanent() bool {
	return e.Code >= permanentCodeFloor
}

// toReplyError strips the server text from SMTP protocol errors.
func toReplyError(err error) (*ReplyError, bool) {
	var protocolErr *textproto.Error
	if !errors.As(err, &protocolErr) {
		return nil, false
	}
	return &ReplyError{Code: protocolErr.Code}, true
}
