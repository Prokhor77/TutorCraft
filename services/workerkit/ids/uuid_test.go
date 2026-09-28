package ids

import (
	"strings"
	"testing"
	"time"

	"github.com/tutorcraft/workerkit/validate"
)

func TestNewV7HasVersionVariantAndFormat(t *testing.T) {
	id := NewV7()
	if !validate.IsUUID(id) {
		t.Fatalf("not a UUID: %q", id)
	}
	if id[14] != '7' {
		t.Fatalf("version nibble = %c, want 7", id[14])
	}
	if !strings.ContainsRune("89ab", rune(id[19])) {
		t.Fatalf("variant nibble = %c, want one of 89ab", id[19])
	}
}

func TestNewV7AtEncodesMillisecondTimestamp(t *testing.T) {
	at := time.UnixMilli(0x0192f3c1a2b3)
	id := NewV7At(at)
	if got := id[:8] + id[9:13]; got != "0192f3c1a2b3" {
		t.Fatalf("timestamp prefix = %s", got)
	}
}

func TestNewV7IsUnique(t *testing.T) {
	seen := map[string]bool{}
	for i := 0; i < 1000; i++ {
		id := NewV7()
		if seen[id] {
			t.Fatalf("duplicate id %s", id)
		}
		seen[id] = true
	}
}
