package ttlcache

import (
	"testing"
	"time"
)

type fakeClock struct{ now time.Time }

func (f *fakeClock) Now() time.Time { return f.now }

func TestEvictsLeastRecentlyUsed(t *testing.T) {
	c := New[int](2, time.Hour)
	c.Put("a", 1)
	c.Put("b", 2)
	c.Get("a") // "b" becomes least recently used
	c.Put("c", 3)
	if c.Contains("b") {
		t.Fatal("b should be evicted")
	}
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatal("a should survive")
	}
	if c.Len() != 2 {
		t.Fatalf("len = %d", c.Len())
	}
}

func TestEntriesExpire(t *testing.T) {
	clock := &fakeClock{now: time.Unix(0, 0)}
	c := newWithClock[string](10, time.Minute, clock.Now)
	c.Put("k", "v")
	clock.now = clock.now.Add(59 * time.Second)
	if !c.Contains("k") {
		t.Fatal("entry expired too early")
	}
	clock.now = clock.now.Add(time.Second)
	if c.Contains("k") {
		t.Fatal("entry should be expired")
	}
	if c.Len() != 0 {
		t.Fatal("expired entry should be removed on access")
	}
}

func TestPutOverwritesAndRefreshes(t *testing.T) {
	c := New[int](2, time.Hour)
	c.Put("a", 1)
	c.Put("a", 2)
	if v, _ := c.Get("a"); v != 2 || c.Len() != 1 {
		t.Fatalf("overwrite failed: v=%d len=%d", v, c.Len())
	}
}
