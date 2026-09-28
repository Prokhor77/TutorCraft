// Package textsafe prepares free-form text (error messages, tool output) for
// logs, events and headers: strips control characters and bounds length.
package textsafe

import (
	"strings"
	"unicode"
)

const ellipsis = "…"

// Truncate cuts s to at most maxRunes runes (ellipsis included).
func Truncate(s string, maxRunes int) string {
	runes := []rune(s)
	if len(runes) <= maxRunes {
		return s
	}
	if maxRunes <= 0 {
		return ""
	}
	return string(runes[:maxRunes-1]) + ellipsis
}

// SingleLine replaces control characters (including newlines) with spaces
// and collapses runs of whitespace.
func SingleLine(s string) string {
	mapped := strings.Map(func(r rune) rune {
		if unicode.IsControl(r) {
			return ' '
		}
		return r
	}, s)
	return strings.Join(strings.Fields(mapped), " ")
}

// Sanitize is SingleLine followed by Truncate.
func Sanitize(s string, maxRunes int) string {
	return Truncate(SingleLine(s), maxRunes)
}
