package validate

import (
	"errors"
	"strings"
	"testing"
)

func TestIsUUID(t *testing.T) {
	cases := map[string]bool{
		"0192f3c1-7b2a-7c3d-8e4f-123456789abc": true,
		"0192F3C1-7B2A-7C3D-8E4F-123456789ABC": true,
		"0192f3c17b2a7c3d8e4f123456789abc":     false,
		"not-a-uuid":                           false,
		"":                                     false,
	}
	for input, want := range cases {
		if got := IsUUID(input); got != want {
			t.Errorf("IsUUID(%q) = %v, want %v", input, got, want)
		}
	}
}

func TestCollectorReportsFieldsWithoutValues(t *testing.T) {
	var c Collector
	c.UUID("fileId", "secret-value")
	c.Required("bucket", " ")
	c.OneOf("status", "weird", "ready", "failed")
	c.MaxLen("title", "абв", 2)
	err := c.Err()
	var verr *Error
	if !errors.As(err, &verr) || len(verr.Fields) != 4 {
		t.Fatalf("want 4 field errors, got %v", err)
	}
	if strings.Contains(err.Error(), "secret-value") || strings.Contains(err.Error(), "weird") {
		t.Fatalf("error leaks values: %s", err)
	}
}

func TestCollectorEmptyIsNil(t *testing.T) {
	var c Collector
	c.UUID("id", "0192f3c1-7b2a-7c3d-8e4f-123456789abc")
	c.MaxLen("title", "абв", 3)
	if err := c.Err(); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}
