package envconfig

import (
	"strings"
	"testing"
	"time"
)

func TestLoaderReadsTypedValues(t *testing.T) {
	l := FromMap(map[string]string{
		"S": " v ", "N": "5", "D": "2m", "L": "a, b,,c", "U": "http://localhost:9000",
	})
	if l.String("S", "x") != "v" || l.String("MISSING", "def") != "def" {
		t.Fatal("string")
	}
	if l.Int("N", 1, 1, 10) != 5 || l.Int("MISSING", 7, 1, 10) != 7 {
		t.Fatal("int")
	}
	if l.Duration("D", time.Second, time.Second, time.Hour) != 2*time.Minute {
		t.Fatal("duration")
	}
	if got := l.RequiredList("L"); strings.Join(got, "|") != "a|b|c" {
		t.Fatalf("list %v", got)
	}
	if u := l.HTTPURL("U", ""); u == nil || u.Host != "localhost:9000" {
		t.Fatal("url")
	}
	if err := l.Err(); err != nil {
		t.Fatalf("unexpected error %v", err)
	}
}

func TestLoaderAccumulatesErrorsWithoutValues(t *testing.T) {
	l := FromMap(map[string]string{"N": "999", "U": "ftp://secret-host", "D": "forever"})
	l.RequiredString("REQ")
	l.Int("N", 1, 1, 10)
	l.HTTPURL("U", "")
	l.Duration("D", time.Second, time.Second, time.Hour)
	err := l.Err()
	if err == nil {
		t.Fatal("expected errors")
	}
	for _, key := range []string{"REQ", "N", "U", "D"} {
		if !strings.Contains(err.Error(), key) {
			t.Errorf("missing %s in %v", key, err)
		}
	}
	if strings.Contains(err.Error(), "secret-host") {
		t.Fatal("error leaks value")
	}
}
