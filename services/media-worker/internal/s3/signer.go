// Package s3 is a minimal S3 client (path-style GET/PUT of objects) signed
// with AWS Signature Version 4, implemented on net/http only.
//
// Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
package s3

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"
)

const (
	algorithm        = "AWS4-HMAC-SHA256"
	serviceName      = "s3"
	terminationToken = "aws4_request"
	secretPrefix     = "AWS4"
	amzDateLayout    = "20060102T150405Z"
	shortDateLayout  = "20060102"

	HeaderAuthorization = "Authorization"
	HeaderAmzDate       = "X-Amz-Date"
	HeaderContentSHA256 = "X-Amz-Content-Sha256"
	headerHost          = "host"

	// EmptyPayloadHash is the SHA-256 of an empty body (GET requests).
	EmptyPayloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	// UnsignedPayload skips body hashing (supported by S3 and MinIO).
	UnsignedPayload = "UNSIGNED-PAYLOAD"
)

// headers that proxies or the transport may alter; never signed.
var unsignedHeaders = map[string]bool{
	"authorization":   true,
	"user-agent":      true,
	"accept-encoding": true,
	"content-length":  true,
	"expect":          true,
	"x-amzn-trace-id": true,
}

// Credentials are static S3 access keys.
type Credentials struct {
	AccessKeyID     string
	SecretAccessKey string
}

// Signer signs requests for one region.
type Signer struct {
	Credentials Credentials
	Region      string
}

// Sign sets X-Amz-Date, X-Amz-Content-Sha256 and Authorization on req.
// payloadHash is the hex SHA-256 of the body, or UnsignedPayload.
func (s Signer) Sign(req *http.Request, payloadHash string, now time.Time) {
	now = now.UTC()
	req.Header.Set(HeaderAmzDate, now.Format(amzDateLayout))
	req.Header.Set(HeaderContentSHA256, payloadHash)
	signedHeaders, canonicalHeaders := canonicalHeaders(req)
	canonical := canonicalRequest(req, canonicalHeaders, signedHeaders, payloadHash)
	scope := s.scope(now)
	toSign := stringToSign(now, scope, canonical)
	signature := hex.EncodeToString(hmacSHA256(s.signingKey(now), toSign))
	req.Header.Set(HeaderAuthorization, algorithm+" Credential="+s.Credentials.AccessKeyID+"/"+scope+
		", SignedHeaders="+signedHeaders+", Signature="+signature)
}

func (s Signer) scope(now time.Time) string {
	return strings.Join([]string{now.Format(shortDateLayout), s.Region, serviceName, terminationToken}, "/")
}

func (s Signer) signingKey(now time.Time) []byte {
	return deriveSigningKey(s.Credentials.SecretAccessKey, now.Format(shortDateLayout), s.Region, serviceName)
}

func deriveSigningKey(secret, date, region, service string) []byte {
	key := hmacSHA256([]byte(secretPrefix+secret), date)
	key = hmacSHA256(key, region)
	key = hmacSHA256(key, service)
	return hmacSHA256(key, terminationToken)
}

func canonicalRequest(req *http.Request, canonicalHeaders, signedHeaders, payloadHash string) string {
	return strings.Join([]string{
		req.Method,
		encodePath(req.URL.Path),
		canonicalQuery(req.URL.Query()),
		canonicalHeaders,
		signedHeaders,
		payloadHash,
	}, "\n")
}

func stringToSign(now time.Time, scope, canonical string) string {
	return strings.Join([]string{algorithm, now.Format(amzDateLayout), scope, sha256Hex([]byte(canonical))}, "\n")
}

// canonicalHeaders returns the signed header list and the canonical header block.
func canonicalHeaders(req *http.Request) (signed string, block string) {
	values := map[string]string{headerHost: hostOf(req)}
	for name, vals := range req.Header {
		lower := strings.ToLower(name)
		if unsignedHeaders[lower] {
			continue
		}
		trimmed := make([]string, 0, len(vals))
		for _, v := range vals {
			trimmed = append(trimmed, strings.Join(strings.Fields(v), " "))
		}
		values[lower] = strings.Join(trimmed, ",")
	}
	names := make([]string, 0, len(values))
	for name := range values {
		names = append(names, name)
	}
	sort.Strings(names)
	var b strings.Builder
	for _, name := range names {
		b.WriteString(name + ":" + values[name] + "\n")
	}
	return strings.Join(names, ";"), b.String()
}

func hostOf(req *http.Request) string {
	if req.Host != "" {
		return req.Host
	}
	return req.URL.Host
}

func canonicalQuery(query url.Values) string {
	pairs := make([]string, 0, len(query))
	for key, vals := range query {
		for _, v := range vals {
			pairs = append(pairs, encode(key, true)+"="+encode(v, true))
		}
	}
	sort.Strings(pairs)
	return strings.Join(pairs, "&")
}

// encodePath URI-encodes every path segment per SigV4 rules (slashes kept).
func encodePath(path string) string {
	if path == "" {
		return "/"
	}
	return encode(path, false)
}

// encode applies RFC 3986 encoding: only A-Z a-z 0-9 - _ . ~ stay literal.
func encode(s string, encodeSlash bool) string {
	const hexDigits = "0123456789ABCDEF"
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		c := s[i]
		if isUnreserved(c) || (c == '/' && !encodeSlash) {
			b.WriteByte(c)
			continue
		}
		b.WriteByte('%')
		b.WriteByte(hexDigits[c>>4])
		b.WriteByte(hexDigits[c&0x0f])
	}
	return b.String()
}

func isUnreserved(c byte) bool {
	return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9') ||
		c == '-' || c == '_' || c == '.' || c == '~'
}

func hmacSHA256(key []byte, data string) []byte {
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte(data)) // hash.Hash.Write never returns an error
	return mac.Sum(nil)
}

func sha256Hex(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}
