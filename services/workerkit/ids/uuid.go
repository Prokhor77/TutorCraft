// Package ids generates UUIDv7 identifiers (ADR-006) without third-party libraries.
package ids

import (
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"time"
)

const (
	uuidBytes       = 16
	timestampBytes  = 6
	version7        = 0x70
	versionMask     = 0x0f
	variantRFC4122  = 0x80
	variantMask     = 0x3f
	versionByteIdx  = 6
	variantByteIdx  = 8
	uint64Bytes     = 8
	timestampOffset = uint64Bytes - timestampBytes
)

// NewV7 returns a new time-ordered UUIDv7 using the current time.
func NewV7() string {
	return NewV7At(time.Now())
}

// NewV7At returns a UUIDv7 whose timestamp is t (millisecond precision).
func NewV7At(t time.Time) string {
	var u [uuidBytes]byte
	// Since Go 1.24 crypto/rand.Read never returns an error (it crashes the
	// program instead), so there is no error to handle here.
	_, _ = rand.Read(u[timestampBytes:])
	var ts [uint64Bytes]byte
	binary.BigEndian.PutUint64(ts[:], uint64(t.UnixMilli()))
	copy(u[:timestampBytes], ts[timestampOffset:])
	u[versionByteIdx] = (u[versionByteIdx] & versionMask) | version7
	u[variantByteIdx] = (u[variantByteIdx] & variantMask) | variantRFC4122
	return format(u)
}

func format(u [uuidBytes]byte) string {
	var buf [36]byte
	hex.Encode(buf[0:8], u[0:4])
	buf[8] = '-'
	hex.Encode(buf[9:13], u[4:6])
	buf[13] = '-'
	hex.Encode(buf[14:18], u[6:8])
	buf[18] = '-'
	hex.Encode(buf[19:23], u[8:10])
	buf[23] = '-'
	hex.Encode(buf[24:], u[10:])
	return string(buf[:])
}
