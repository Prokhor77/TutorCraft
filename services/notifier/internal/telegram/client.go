// Package telegram implements the Telegram channel: a Bot API client, message
// formatting, the notification sender and the /start linking bot.
package telegram

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const (
	DefaultBaseURL       = "https://api.telegram.org"
	methodSendMessage    = "sendMessage"
	methodGetUpdates     = "getUpdates"
	maxResponseBytes     = 1 << 20
	redactedToken        = "<redacted>"
	contentTypeJSON      = "application/json"
	headerContentType    = "Content-Type"
	codeTooManyRequests  = http.StatusTooManyRequests
	firstServerErrorCode = http.StatusInternalServerError
)

// APIError is an unsuccessful Bot API response.
type APIError struct {
	Method      string
	Code        int
	Description string
	RetryAfter  time.Duration
}

func (e *APIError) Error() string {
	return fmt.Sprintf("telegram %s: %d %s", e.Method, e.Code, e.Description)
}

// TransportError is a network failure; its text never contains the bot token.
type TransportError struct {
	Method  string
	Message string
}

func (e *TransportError) Error() string {
	return "telegram " + e.Method + ": " + e.Message
}

// Client calls the Bot API. The token only ever appears in the request URL.
type Client struct {
	baseURL string
	token   string
	http    *http.Client
}

// NewClient creates a client; requests are bounded by their contexts.
func NewClient(baseURL, token string) *Client {
	return &Client{baseURL: strings.TrimSuffix(baseURL, "/"), token: token, http: &http.Client{}}
}

type apiResponse struct {
	OK          bool            `json:"ok"`
	Result      json.RawMessage `json:"result"`
	ErrorCode   int             `json:"error_code"`
	Description string          `json:"description"`
	Parameters  *struct {
		RetryAfter int `json:"retry_after"`
	} `json:"parameters"`
}

// SendMessage calls sendMessage.
func (c *Client) SendMessage(ctx context.Context, req SendMessageRequest) error {
	return c.call(ctx, methodSendMessage, req, nil)
}

// GetUpdates long-polls for updates starting at offset.
func (c *Client) GetUpdates(ctx context.Context, offset int64, timeoutSeconds int) ([]Update, error) {
	var updates []Update
	req := getUpdatesRequest{Offset: offset, Timeout: timeoutSeconds, AllowedUpdates: []string{"message"}}
	err := c.call(ctx, methodGetUpdates, req, &updates)
	return updates, err
}

func (c *Client) call(ctx context.Context, method string, payload, result any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("telegram %s: encode request: %w", method, err)
	}
	endpoint := c.baseURL + "/bot" + c.token + "/" + method
	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return c.transportError(method, err)
	}
	httpReq.Header.Set(headerContentType, contentTypeJSON)
	resp, err := c.http.Do(httpReq)
	if err != nil {
		return c.transportError(method, err)
	}
	defer resp.Body.Close()
	return decodeResponse(method, resp, result)
}

func decodeResponse(method string, resp *http.Response, result any) error {
	var parsed apiResponse
	if err := json.NewDecoder(io.LimitReader(resp.Body, maxResponseBytes)).Decode(&parsed); err != nil {
		return &APIError{Method: method, Code: resp.StatusCode, Description: "unreadable response"}
	}
	if !parsed.OK {
		apiErr := &APIError{Method: method, Code: parsed.ErrorCode, Description: parsed.Description}
		if parsed.Parameters != nil {
			apiErr.RetryAfter = time.Duration(parsed.Parameters.RetryAfter) * time.Second
		}
		return apiErr
	}
	if result == nil {
		return nil
	}
	if err := json.Unmarshal(parsed.Result, result); err != nil {
		return fmt.Errorf("telegram %s: decode result: %w", method, err)
	}
	return nil
}

// transportError drops *url.Error (whose text contains the URL, i.e. the
// token) and scrubs the token from whatever remains.
func (c *Client) transportError(method string, err error) error {
	var urlErr *url.Error
	if errors.As(err, &urlErr) {
		err = urlErr.Err
	}
	message := err.Error()
	if c.token != "" {
		message = strings.ReplaceAll(message, c.token, redactedToken)
	}
	return &TransportError{Method: method, Message: message}
}
