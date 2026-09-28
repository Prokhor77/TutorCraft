package s3

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

const (
	headerContentType     = "Content-Type"
	responseHeaderTimeout = 30 * time.Second
	maxErrorBodyBytes     = 64 << 10
	filePermissions       = 0o600
)

// ErrObjectTooLarge is returned by DownloadFile when the object exceeds the limit.
var ErrObjectTooLarge = errors.New("s3: object exceeds size limit")

// StatusError is a non-2xx S3 response.
type StatusError struct {
	Operation  string
	StatusCode int
	Code       string // S3 error code, e.g. "NoSuchKey"
}

func (e *StatusError) Error() string {
	return fmt.Sprintf("s3 %s: HTTP %d %s", e.Operation, e.StatusCode, e.Code)
}

// IsNotFound reports whether err is an S3 404 (missing key or bucket).
func IsNotFound(err error) bool {
	var statusErr *StatusError
	return errors.As(err, &statusErr) && statusErr.StatusCode == http.StatusNotFound
}

// Config holds connection settings.
type Config struct {
	Endpoint    *url.URL // e.g. http://minio:9000
	Region      string
	Credentials Credentials
}

// Client performs path-style object requests: {endpoint}/{bucket}/{key}.
type Client struct {
	endpoint *url.URL
	signer   Signer
	http     *http.Client
	now      func() time.Time
}

// NewClient creates a client. Requests are bounded by their context; the
// transport only limits the wait for response headers.
func NewClient(cfg Config) *Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.ResponseHeaderTimeout = responseHeaderTimeout
	return &Client{
		endpoint: cfg.Endpoint,
		signer:   Signer{Credentials: cfg.Credentials, Region: cfg.Region},
		http:     &http.Client{Transport: transport},
		now:      time.Now,
	}
}

func (c *Client) objectURL(bucket, key string) *url.URL {
	u := *c.endpoint
	u.Path = strings.TrimSuffix(c.endpoint.Path, "/") + "/" + bucket + "/" + key
	u.RawPath = encodePath(u.Path)
	u.RawQuery = ""
	return &u
}

// DownloadFile streams an object into a new file at path, refusing objects
// larger than maxBytes.
func (c *Client) DownloadFile(ctx context.Context, bucket, key, path string, maxBytes int64) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.objectURL(bucket, key).String(), nil)
	if err != nil {
		return fmt.Errorf("s3 get: build request: %w", err)
	}
	resp, err := c.send(req, EmptyPayloadHash, "get")
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.ContentLength > maxBytes {
		return ErrObjectTooLarge
	}
	return writeLimited(path, resp.Body, maxBytes)
}

func writeLimited(path string, body io.Reader, maxBytes int64) error {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, filePermissions)
	if err != nil {
		return fmt.Errorf("s3 get: create file: %w", err)
	}
	written, copyErr := io.Copy(file, io.LimitReader(body, maxBytes+1))
	closeErr := file.Close()
	if copyErr != nil {
		return fmt.Errorf("s3 get: read body: %w", copyErr)
	}
	if closeErr != nil {
		return fmt.Errorf("s3 get: close file: %w", closeErr)
	}
	if written > maxBytes {
		return ErrObjectTooLarge
	}
	return nil
}

// UploadFile stores the file at path under key with the given Content-Type.
func (c *Client) UploadFile(ctx context.Context, bucket, key, path, contentType string) error {
	payloadHash, size, err := hashFile(path)
	if err != nil {
		return err
	}
	file, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("s3 put: open file: %w", err)
	}
	defer file.Close()
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, c.objectURL(bucket, key).String(), file)
	if err != nil {
		return fmt.Errorf("s3 put: build request: %w", err)
	}
	req.ContentLength = size
	req.Header.Set(headerContentType, contentType)
	resp, err := c.send(req, payloadHash, "put")
	if err != nil {
		return err
	}
	return drainAndClose(resp.Body)
}

func hashFile(path string) (string, int64, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", 0, fmt.Errorf("s3 put: open file: %w", err)
	}
	defer file.Close()
	hash := sha256.New()
	size, err := io.Copy(hash, file)
	if err != nil {
		return "", 0, fmt.Errorf("s3 put: hash file: %w", err)
	}
	return hex.EncodeToString(hash.Sum(nil)), size, nil
}

// send signs and executes req; non-2xx responses become *StatusError.
func (c *Client) send(req *http.Request, payloadHash, operation string) (*http.Response, error) {
	c.signer.Sign(req, payloadHash, c.now())
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("s3 %s: %w", operation, err)
	}
	if resp.StatusCode >= http.StatusOK && resp.StatusCode < http.StatusMultipleChoices {
		return resp, nil
	}
	defer resp.Body.Close()
	return nil, &StatusError{Operation: operation, StatusCode: resp.StatusCode, Code: errorCode(resp.Body)}
}

// errorCode extracts <Code> from an S3 XML error body (best effort).
func errorCode(body io.Reader) string {
	var parsed struct {
		Code string `xml:"Code"`
	}
	if err := xml.NewDecoder(io.LimitReader(body, maxErrorBodyBytes)).Decode(&parsed); err != nil {
		return ""
	}
	return parsed.Code
}

func drainAndClose(body io.ReadCloser) error {
	_, copyErr := io.Copy(io.Discard, io.LimitReader(body, maxErrorBodyBytes))
	closeErr := body.Close()
	return errors.Join(copyErr, closeErr)
}
