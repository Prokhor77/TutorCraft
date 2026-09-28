package com.tutorcraft.core.integrations.domain;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Защита от SSRF для URL вебхуков: только https; http — лишь для localhost при явном разрешении (dev);
 * без учётных данных в URL; все адреса хоста должны быть публичными (не loopback, частные, link-local,
 * CGNAT, multicast, зарезервированные, IPv6 ULA). Проверка выполняется при создании и перед каждой отправкой.
 */
public final class WebhookUrlGuard {

    public enum Verdict { ALLOWED, INVALID_URL, SCHEME_NOT_ALLOWED, CREDENTIALS_NOT_ALLOWED, UNRESOLVABLE_HOST, PRIVATE_ADDRESS }

    @FunctionalInterface
    public interface HostResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");
    private static final int BYTE_MASK = 0xFF;
    private static final int CGNAT_FIRST_OCTET = 100;
    private static final int CGNAT_SECOND_MASK = 0xC0;
    private static final int CGNAT_SECOND_VALUE = 64;
    private static final int BENCHMARK_FIRST_OCTET = 198;
    private static final int BENCHMARK_SECOND_MASK = 0xFE;
    private static final int BENCHMARK_SECOND_VALUE = 18;
    private static final int RESERVED_FROM_OCTET = 240;
    private static final int ULA_MASK = 0xFE;
    private static final int ULA_VALUE = 0xFC;

    private final HostResolver resolver;
    private final boolean allowLocalhost;

    public WebhookUrlGuard(HostResolver resolver, boolean allowLocalhost) {
        this.resolver = resolver;
        this.allowLocalhost = allowLocalhost;
    }

    public Verdict check(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return Verdict.INVALID_URL;
        }
        if (uri.getRawUserInfo() != null) {
            return Verdict.CREDENTIALS_NOT_ALLOWED;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = stripBrackets(uri.getHost().toLowerCase(Locale.ROOT));
        if (allowLocalhost && LOCAL_HOSTS.contains(host)) {
            return HTTP.equals(scheme) || HTTPS.equals(scheme) ? Verdict.ALLOWED : Verdict.SCHEME_NOT_ALLOWED;
        }
        if (!HTTPS.equals(scheme)) {
            return Verdict.SCHEME_NOT_ALLOWED;
        }
        return checkAddresses(host);
    }

    private Verdict checkAddresses(String host) {
        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            return Verdict.UNRESOLVABLE_HOST;
        }
        if (addresses == null || addresses.isEmpty()) {
            return Verdict.UNRESOLVABLE_HOST;
        }
        return addresses.stream().allMatch(WebhookUrlGuard::isPublic) ? Verdict.ALLOWED : Verdict.PRIVATE_ADDRESS;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        return address instanceof Inet4Address ? isPublicV4(bytes) : !isUniqueLocalV6(bytes);
    }

    private static boolean isPublicV4(byte[] bytes) {
        int first = bytes[0] & BYTE_MASK;
        int second = bytes[1] & BYTE_MASK;
        boolean thisNetwork = first == 0;
        boolean carrierGradeNat = first == CGNAT_FIRST_OCTET && (second & CGNAT_SECOND_MASK) == CGNAT_SECOND_VALUE;
        boolean benchmarking = first == BENCHMARK_FIRST_OCTET && (second & BENCHMARK_SECOND_MASK) == BENCHMARK_SECOND_VALUE;
        boolean reserved = first >= RESERVED_FROM_OCTET;
        return !(thisNetwork || carrierGradeNat || benchmarking || reserved);
    }

    private static boolean isUniqueLocalV6(byte[] bytes) {
        return ((bytes[0] & BYTE_MASK) & ULA_MASK) == ULA_VALUE;
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return new URI(url.trim());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }
}
