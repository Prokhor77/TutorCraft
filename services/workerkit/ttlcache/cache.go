// Package ttlcache is a small thread-safe LRU cache with per-entry TTL.
//
// It backs in-memory idempotency (processed eventIds). Being in-memory, it
// only deduplicates redeliveries seen by the same process; see service READMEs.
package ttlcache

import (
	"container/list"
	"sync"
	"time"
)

// Cache is an LRU cache whose entries expire after a fixed TTL.
type Cache[V any] struct {
	mu       sync.Mutex
	capacity int
	ttl      time.Duration
	now      func() time.Time
	items    map[string]*list.Element
	order    *list.List // front = most recently used
}

type entry[V any] struct {
	key       string
	value     V
	expiresAt time.Time
}

// New creates a cache holding at most capacity entries, each for ttl.
func New[V any](capacity int, ttl time.Duration) *Cache[V] {
	return newWithClock[V](capacity, ttl, time.Now)
}

func newWithClock[V any](capacity int, ttl time.Duration, now func() time.Time) *Cache[V] {
	return &Cache[V]{
		capacity: capacity,
		ttl:      ttl,
		now:      now,
		items:    make(map[string]*list.Element, capacity),
		order:    list.New(),
	}
}

// Get returns the live value for key.
func (c *Cache[V]) Get(key string) (V, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	var zero V
	element, ok := c.items[key]
	if !ok {
		return zero, false
	}
	item := element.Value.(*entry[V])
	if !c.now().Before(item.expiresAt) {
		c.remove(element)
		return zero, false
	}
	c.order.MoveToFront(element)
	return item.value, true
}

// Contains reports whether a live entry exists for key.
func (c *Cache[V]) Contains(key string) bool {
	_, ok := c.Get(key)
	return ok
}

// Put stores value under key, evicting the least recently used entry when full.
func (c *Cache[V]) Put(key string, value V) {
	c.mu.Lock()
	defer c.mu.Unlock()
	expiresAt := c.now().Add(c.ttl)
	if element, ok := c.items[key]; ok {
		element.Value = &entry[V]{key: key, value: value, expiresAt: expiresAt}
		c.order.MoveToFront(element)
		return
	}
	c.items[key] = c.order.PushFront(&entry[V]{key: key, value: value, expiresAt: expiresAt})
	for c.order.Len() > c.capacity {
		c.remove(c.order.Back())
	}
}

// Len returns the number of stored entries (including not yet evicted expired ones).
func (c *Cache[V]) Len() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.order.Len()
}

func (c *Cache[V]) remove(element *list.Element) {
	c.order.Remove(element)
	delete(c.items, element.Value.(*entry[V]).key)
}
