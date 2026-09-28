package notification

import (
	"net/url"
	"strings"
)

// LinkResolver turns app-relative links into absolute URLs for email and Telegram.
type LinkResolver struct {
	base string // PUBLIC_BASE_URL without trailing slash
}

// NewLinkResolver creates a resolver for the public web origin.
func NewLinkResolver(publicBaseURL *url.URL) LinkResolver {
	return LinkResolver{base: strings.TrimSuffix(publicBaseURL.String(), "/")}
}

// Absolute returns an absolute URL for link, or "" when there is no link.
func (l LinkResolver) Absolute(link string) string {
	if link == "" || !strings.HasPrefix(link, "/") {
		return link
	}
	return l.base + link
}
