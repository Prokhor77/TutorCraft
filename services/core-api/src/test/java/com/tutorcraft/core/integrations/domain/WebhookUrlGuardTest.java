package com.tutorcraft.core.integrations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.integrations.domain.WebhookUrlGuard.Verdict;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class WebhookUrlGuardTest {

    /** Фиктивный DNS: имена → адреса; IP-литералы разбираются без обращения к сети. */
    private static final Map<String, String> DNS = Map.of(
            "hooks.example.com", "93.184.216.34",
            "internal.example.com", "10.0.0.5",
            "rebind.example.com", "169.254.169.254");

    private static final WebhookUrlGuard.HostResolver RESOLVER = host -> {
        String address = DNS.getOrDefault(host, host);
        if (!address.matches("[0-9a-fA-F:.]+")) {
            throw new UnknownHostException(host);
        }
        return List.of(InetAddress.getByName(address));
    };

    private final WebhookUrlGuard prod = new WebhookUrlGuard(RESOLVER, false);
    private final WebhookUrlGuard dev = new WebhookUrlGuard(RESOLVER, true);

    @Test
    void allowsHttpsToPublicHost() {
        assertThat(prod.check("https://hooks.example.com/tc?x=1")).isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void rejectsPlainHttpAndOtherSchemes() {
        assertThat(prod.check("http://hooks.example.com/")).isEqualTo(Verdict.SCHEME_NOT_ALLOWED);
        assertThat(prod.check("ftp://hooks.example.com/")).isEqualTo(Verdict.SCHEME_NOT_ALLOWED);
    }

    @Test
    void rejectsHostsResolvingToPrivateAddresses() {
        assertThat(prod.check("https://internal.example.com/")).isEqualTo(Verdict.PRIVATE_ADDRESS);
        assertThat(prod.check("https://rebind.example.com/")).isEqualTo(Verdict.PRIVATE_ADDRESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://127.0.0.1/", "https://10.1.2.3/", "https://172.16.0.1/", "https://192.168.1.1/", "https://169.254.169.254/",
        "https://100.64.0.1/", "https://0.0.0.0/", "https://224.0.0.1/", "https://240.0.0.1/", "https://198.18.0.1/",
        "https://[::1]/", "https://[fd00::1]/", "https://[fe80::1]/", "https://[::ffff:10.0.0.1]/"
    })
    void rejectsPrivateAndReservedLiterals(String url) {
        assertThat(prod.check(url)).isEqualTo(Verdict.PRIVATE_ADDRESS);
    }

    @Test
    void localhostOnlyInDevMode() {
        assertThat(prod.check("http://localhost:4000/hook")).isEqualTo(Verdict.SCHEME_NOT_ALLOWED);
        assertThat(prod.check("https://localhost/hook")).isEqualTo(Verdict.UNRESOLVABLE_HOST);
        assertThat(dev.check("http://localhost:4000/hook")).isEqualTo(Verdict.ALLOWED);
        assertThat(dev.check("http://127.0.0.1:4000/hook")).isEqualTo(Verdict.ALLOWED);
        assertThat(dev.check("http://10.0.0.1/hook")).isEqualTo(Verdict.SCHEME_NOT_ALLOWED);
    }

    @Test
    void rejectsCredentialsAndMalformedUrls() {
        assertThat(prod.check("https://user:pass@hooks.example.com/")).isEqualTo(Verdict.CREDENTIALS_NOT_ALLOWED);
        assertThat(prod.check("not a url")).isEqualTo(Verdict.INVALID_URL);
        assertThat(prod.check("/relative/path")).isEqualTo(Verdict.INVALID_URL);
        assertThat(prod.check(null)).isEqualTo(Verdict.INVALID_URL);
    }

    @Test
    void unresolvableHostIsRejected() {
        assertThat(prod.check("https://no-such-host.invalid/")).isEqualTo(Verdict.UNRESOLVABLE_HOST);
    }
}
