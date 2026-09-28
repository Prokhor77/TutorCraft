package auth

import (
	"errors"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	secret   = "0123456789abcdef0123456789abcdef-test-secret"
	userID   = "0192f3c1-0000-7000-8000-000000000002"
	tenantID = "0192f3c1-0000-7000-8000-000000000001"
)

var now = time.Date(2026, 9, 27, 18, 0, 0, 0, time.UTC)

func claims() jwt.MapClaims {
	return jwt.MapClaims{
		"iss": Issuer, "sub": userID, "tid": tenantID, "typ": AccessTokenType, "troles": []string{"teacher"},
		"iat": now.Add(-time.Minute).Unix(), "exp": now.Add(15 * time.Minute).Unix(),
	}
}

func sign(t *testing.T, method jwt.SigningMethod, key any, c jwt.MapClaims) string {
	t.Helper()
	token, err := jwt.NewWithClaims(method, c).SignedString(key)
	if err != nil {
		t.Fatal(err)
	}
	return token
}

func validator() *Validator {
	return NewValidator(secret, func() time.Time { return now })
}

func TestValidTokenYieldsPrincipal(t *testing.T) {
	p, err := validator().Validate(sign(t, jwt.SigningMethodHS256, []byte(secret), claims()))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if p.UserID != userID || p.TenantID != tenantID || !p.ExpiresAt.Equal(now.Add(15*time.Minute)) {
		t.Fatalf("unexpected principal %+v", p)
	}
}

func TestRejectsInvalidTokens(t *testing.T) {
	with := func(key string, value any) jwt.MapClaims {
		c := claims()
		if value == nil {
			delete(c, key)
		} else {
			c[key] = value
		}
		return c
	}
	cases := map[string]string{
		"expired":         sign(t, jwt.SigningMethodHS256, []byte(secret), with("exp", now.Add(-time.Hour).Unix())),
		"no expiry":       sign(t, jwt.SigningMethodHS256, []byte(secret), with("exp", nil)),
		"wrong secret":    sign(t, jwt.SigningMethodHS256, []byte("another-secret-another-secret-00"), claims()),
		"wrong alg HS512": sign(t, jwt.SigningMethodHS512, []byte(secret), claims()),
		"alg none":        sign(t, jwt.SigningMethodNone, jwt.UnsafeAllowNoneSignatureType, claims()),
		"wrong issuer":    sign(t, jwt.SigningMethodHS256, []byte(secret), with("iss", "evil")),
		"refresh type":    sign(t, jwt.SigningMethodHS256, []byte(secret), with("typ", "pat")),
		"sub not uuid":    sign(t, jwt.SigningMethodHS256, []byte(secret), with("sub", "admin")),
		"missing tenant":  sign(t, jwt.SigningMethodHS256, []byte(secret), with("tid", nil)),
		"garbage":         "not.a.jwt",
		"empty":           "",
	}
	for name, token := range cases {
		if _, err := validator().Validate(token); !errors.Is(err, ErrInvalidToken) {
			t.Errorf("%s: want ErrInvalidToken, got %v", name, err)
		}
	}
}

func TestLeewayToleratesSmallClockSkew(t *testing.T) {
	c := claims()
	c["exp"] = now.Add(-10 * time.Second).Unix()
	if _, err := validator().Validate(sign(t, jwt.SigningMethodHS256, []byte(secret), c)); err != nil {
		t.Fatalf("10s skew should be tolerated: %v", err)
	}
}
