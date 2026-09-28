package ffmpeg

import (
	"bytes"
	"errors"
)

var errOutputTooLarge = errors.New("tool output exceeds limit")

// tailBuffer keeps only the last limit bytes written (stderr diagnostics).
type tailBuffer struct {
	data  []byte
	limit int
}

func (t *tailBuffer) Write(p []byte) (int, error) {
	t.data = append(t.data, p...)
	if overflow := len(t.data) - t.limit; overflow > 0 {
		t.data = t.data[overflow:]
	}
	return len(p), nil
}

func (t *tailBuffer) String() string {
	return string(t.data)
}

// limitedBuffer fails once more than limit bytes are written (bounded stdout).
type limitedBuffer struct {
	buf   *bytes.Buffer
	limit int
}

func (l *limitedBuffer) Write(p []byte) (int, error) {
	if l.buf.Len()+len(p) > l.limit {
		return 0, errOutputTooLarge
	}
	return l.buf.Write(p)
}
