package textsafe

import "testing"

func TestSanitize(t *testing.T) {
	cases := []struct {
		in   string
		max  int
		want string
	}{
		{"line1\nline2\r\n\tend", 100, "line1 line2 end"},
		{"абвгд", 3, "аб…"},
		{"short", 5, "short"},
		{"x\x00y", 10, "x y"},
	}
	for _, c := range cases {
		if got := Sanitize(c.in, c.max); got != c.want {
			t.Errorf("Sanitize(%q, %d) = %q, want %q", c.in, c.max, got, c.want)
		}
	}
}
