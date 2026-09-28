package com.tutorcraft.core.shared.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Политика URL пользовательского контента (NFR-SEC-03): ссылки — только http/https/mailto,
 * встраивание (iframe) — только http/https на домены из белого списка tenant.
 */
public final class UrlPolicy {

    public static final int MAX_URL_LENGTH = 2048;

    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String MAILTO = "mailto";
    private static final Set<String> WEB_SCHEMES = Set.of(HTTP, HTTPS);
    private static final Set<String> HREF_SCHEMES = Set.of(HTTP, HTTPS, MAILTO);

    private UrlPolicy() {
    }

    /** Абсолютный http(s)-URL с хостом. */
    public static boolean isWebUrl(String url) {
        return parse(url).filter(uri -> WEB_SCHEMES.contains(scheme(uri)) && uri.getHost() != null).isPresent();
    }

    /** Ссылка в тексте: http(s) с хостом или mailto. javascript:, data: и относительные ссылки запрещены. */
    public static boolean isSafeHref(String url) {
        return parse(url).filter(uri -> HREF_SCHEMES.contains(scheme(uri))
                && (MAILTO.equals(scheme(uri)) || uri.getHost() != null)).isPresent();
    }

    /** URL для iframe: http(s) и хост из белого списка (точное совпадение, без учёта регистра). */
    public static boolean isAllowedEmbed(String url, Set<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty() || !isWebUrl(url)) {
            return false;
        }
        Set<String> hosts = whitelist.stream().map(host -> host.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return parse(url).map(uri -> uri.getHost().toLowerCase(Locale.ROOT)).filter(hosts::contains).isPresent();
    }

    private static Optional<URI> parse(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH || containsForbiddenChars(url)) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url);
            return uri.getScheme() == null ? Optional.empty() : Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static String scheme(URI uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT);
    }

    private static boolean containsForbiddenChars(String url) {
        return url.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
    }
}
