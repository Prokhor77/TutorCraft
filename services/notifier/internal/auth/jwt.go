// Package auth validates access JWTs issued by core-api (ADR-003):
// HS256 with JWT_SECRET, claims sub=userId, tid=tenantId, typ=access,
// iss=tutorcraft-core, exp. Claim names mirror JwtClaims.java.
package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/tutorcraft/workerkit/validate"
)

const (
	Issuer          = "tutorcraft-core"
	AccessTokenType = "access"
	MinSecretBytes  = 32
	clockSkewLeeway = 30 * time.Second
)

var (
	ErrInvalidToken = errors.New("invalid access token")
	signingMethods  = []string{jwt.SigningMethodHS256.Alg()}
)

// Principal is the authenticated user of a connection.
type Principal struct {
	UserID    string
	TenantID  string
	ExpiresAt time.Time
}

type accessClaims struct {
	jwt.RegisteredClaims
	TenantID  string `json:"tid"`
	TokenType string `json:"typ"`
}

// Validator checks access tokens.
type Validator struct {
	secret []byte
	now    func() time.Time
}

// NewValidator creates a validator; the secret is used as raw UTF-8 bytes,
// exactly like core-api's JwtService.
func NewValidator(secret string, now func() time.Time) *Validator {
	return &Validator{secret: []byte(secret), now: now}
}

// Validate verifies signature, algorithm, issuer, expiry and claim formats.
// Every failure is reported as ErrInvalidToken (details must not reach clients).
func (v *Validator) Validate(token string) (Principal, error) {
	var claims accessClaims
	_, err := jwt.ParseWithClaims(token, &claims, v.key,
		jwt.WithValidMethods(signingMethods),
		jwt.WithIssuer(Issuer),
		jwt.WithExpirationRequired(),
		jwt.WithLeeway(clockSkewLeeway),
		jwt.WithTimeFunc(v.now),
	)
	if err != nil || !validClaims(claims) {
		return Principal{}, ErrInvalidToken
	}
	return Principal{UserID: claims.Subject, TenantID: claims.TenantID, ExpiresAt: claims.ExpiresAt.Time}, nil
}

func (v *Validator) key(*jwt.Token) (any, error) {
	return v.secret, nil
}

func validClaims(c accessClaims) bool {
	return c.TokenType == AccessTokenType && validate.IsUUID(c.Subject) && validate.IsUUID(c.TenantID)
}
