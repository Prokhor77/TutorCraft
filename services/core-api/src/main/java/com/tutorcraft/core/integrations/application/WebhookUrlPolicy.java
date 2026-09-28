package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.integrations.domain.WebhookUrlGuard;
import com.tutorcraft.core.integrations.domain.WebhookUrlGuard.Verdict;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Проверка URL вебхука против SSRF с реальным DNS. http://localhost разрешается только в dev (allow-localhost). */
@Component
public class WebhookUrlPolicy {

    private static final String URL_FIELD = "url";

    private final WebhookUrlGuard guard;

    public WebhookUrlPolicy(@Value("${tutorcraft.webhooks.allow-localhost:false}") boolean allowLocalhost) {
        this.guard = new WebhookUrlGuard(host -> Arrays.asList(InetAddress.getAllByName(host)), allowLocalhost);
    }

    public Verdict check(String url) {
        return guard.check(url);
    }

    public void require(String url) {
        Verdict verdict = check(url);
        if (verdict != Verdict.ALLOWED) {
            String code = verdict.name().toLowerCase(Locale.ROOT);
            throw ValidationException.single(URL_FIELD, code, "Webhook URL is not allowed: " + code);
        }
    }
}
